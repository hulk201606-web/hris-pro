package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.recruit.dto.ApplicationDtos.ApplicationCreateRequest;
import com.hris.recruit.dto.ApplicationDtos.ApplicationResponse;
import com.hris.recruit.dto.ApplicationDtos.ApplicationStageRequest;
import com.hris.recruit.dto.ApplicationDtos.ApplicationUpdateRequest;
import com.hris.recruit.entity.Application;
import com.hris.recruit.entity.StageLog;
import com.hris.recruit.enums.RejectReason;
import com.hris.recruit.mapper.ApplicationMapper;
import com.hris.recruit.mapper.StageLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 投递记录（Application）：候选人对某职位发布的一次申请，含招聘阶段 stage 与投递状态。
 *
 * <p>RCS-F-012 初筛与标记：PASS 可推进阶段并给候选人打标签（复用 rcu_talent_pool_tag，
 * uk 幂等）；REJECT 必须携带结构化原因（OTHER 额外要求 note），落 application.reason/
 * reason_note 并写 rcu_stage_log 留痕。阶段流转仅经 {@link #advance}，CRUD update 不承担
 * 该语义。</p>
 *
 * <p>业务域：招聘流程（recruit）；分层：Service。</p>
 */
@Service
public class ApplicationService {

    /** 应用状态。 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REJECTED = "REJECTED";

    /** 流转动作。 */
    public static final String ACTION_PASS = "PASS";
    public static final String ACTION_REJECT = "REJECT";

    private static final Set<String> STAGES =
            Set.of("APPLIED", "SCREEN", "INTERVIEW", "OFFER", "HIRED");

    private final ApplicationMapper mapper;
    private final StageLogMapper stageLogMapper;
    private final TalentPoolTagService tagService;

    public ApplicationService(ApplicationMapper mapper, StageLogMapper stageLogMapper,
                              TalentPoolTagService tagService) {
        this.mapper = mapper;
        this.stageLogMapper = stageLogMapper;
        this.tagService = tagService;
    }

    /** 按 ID 查询，不存在抛 NOT_FOUND。 */
    public ApplicationResponse get(Long id) {
        return toResponse(requireExists(id));
    }

    /** 分页查询列表。 */
    public PageResult<ApplicationResponse> list(long page, long size) {
        IPage<Application> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<ApplicationResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建记录（事务）。 */
    @Transactional(rollbackFor = Exception.class)
    public ApplicationResponse create(ApplicationCreateRequest req) {
        Application e = new Application();
        e.setCandidateId(req.candidateId());
        e.setOpeningId(req.openingId());
        e.setChannelId(req.channelId());
        e.setStage(req.stage());
        e.setStatus(req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新记录（事务）；数据被并发变更时抛 RCS_ERROR。 */
    @Transactional(rollbackFor = Exception.class)
    public ApplicationResponse update(Long id, ApplicationUpdateRequest req) {
        Application e = requireExists(id);
        e.setCandidateId(req.candidateId());
        e.setOpeningId(req.openingId());
        e.setChannelId(req.channelId());
        e.setStage(req.stage());
        e.setStatus(req.status());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除记录（事务）；不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Application 不存在");
        }
    }

    /**
     * 初筛流转（RCS-F-012，事务）：
     * <ul>
     *   <li>PASS：可推进到 toStage（缺省保持当前阶段），并为候选人幂等追加 tags；</li>
     *   <li>REJECT：reason 必填且属于枚举，OTHER 必须有 note；状态置 REJECTED。</li>
     * </ul>
     * 两种动作均写 rcu_stage_log 留痕（remark 携带原因/标签摘要）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApplicationResponse advance(Long id, ApplicationStageRequest req) {
        Application e = requireExists(id);
        String action = req.action() == null ? "" : req.action().trim().toUpperCase(Locale.ROOT);
        String fromStage = e.getStage();
        String remark;
        if (ACTION_REJECT.equals(action)) {
            RejectReason reason = parseReason(req.reason());
            String note = trimToNull(req.note());
            if (reason.isNoteRequired() && note == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "OTHER 原因必须填写 note");
            }
            e.setStatus(STATUS_REJECTED);
            e.setReason(reason.name());
            e.setReasonNote(note);
            remark = "淘汰：" + reason.name() + (note == null ? "" : "（" + note + "）");
        } else if (ACTION_PASS.equals(action)) {
            String toStage = trimToNull(req.toStage());
            if (toStage != null) {
                e.setStage(validateStage(toStage));
            }
            e.setStatus(STATUS_ACTIVE);
            List<String> applied = applyTags(e.getCandidateId(), req.tags());
            remark = "通过" + (applied.isEmpty() ? "" : "，标签：" + String.join(",", applied));
        } else {
            throw new BizException(ErrorCode.PARAM_INVALID, "action 仅支持 PASS/REJECT");
        }
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        writeStageLog(e.getId(), fromStage, e.getStage(), remark);
        return toResponse(e);
    }

    /** 幂等打标签：去重后逐条 addIfAbsent，返回本次实际新增的标签。 */
    private List<String> applyTags(Long candidateId, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            String v = trimToNull(tag);
            if (v != null) {
                unique.add(v);
            }
        }
        List<String> added = new java.util.ArrayList<>();
        for (String tag : unique) {
            if (tagService.addIfAbsent(candidateId, tag)) {
                added.add(tag);
            }
        }
        return added;
    }

    private void writeStageLog(Long applicationId, String fromStage, String toStage, String remark) {
        StageLog log = new StageLog();
        log.setApplicationId(applicationId);
        log.setFromStage(fromStage);
        log.setToStage(toStage == null ? fromStage : toStage);
        log.setRemark(remark.length() > 255 ? remark.substring(0, 255) : remark);
        stageLogMapper.insert(log);
    }

    private RejectReason parseReason(String reason) {
        String value = trimToNull(reason);
        if (value == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "淘汰必须填写 reason");
        }
        try {
            return RejectReason.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "reason 非法：" + value);
        }
    }

    private String validateStage(String stage) {
        String value = stage.trim().toUpperCase(Locale.ROOT);
        if (!STAGES.contains(value)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "toStage 仅支持 APPLIED/SCREEN/INTERVIEW/OFFER/HIRED");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private Application requireExists(Long id) {
        Application e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Application 不存在");
        }
        return e;
    }

    private ApplicationResponse toResponse(Application e) {
        return new ApplicationResponse(e.getId(), e.getCandidateId(), e.getOpeningId(), e.getChannelId(),
                e.getStage(), e.getStatus(), e.getReason(), e.getReasonNote(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
