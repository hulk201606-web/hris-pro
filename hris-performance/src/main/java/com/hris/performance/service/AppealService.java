package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.performance.dto.AppealDtos.AppealCreateRequest;
import com.hris.performance.dto.AppealDtos.AppealResponse;
import com.hris.performance.dto.AppealDtos.AppealUpdateRequest;
import com.hris.performance.entity.Appeal;
import com.hris.performance.mapper.AppealMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Appeal 服务（docs/19 §9 遗留项：PRF_APPEAL 审批发起接入）。
 *
 * <p>服务端强制语义：创建即提交——status 恒置 PROCESSING、biz_key 服务端生成
 * （前缀 + 雪花 ID，唯一键 uk_prf_appeal_biz 兜底，冲突自动重试），随即发起
 * PRF_APPEAL 流程并把实例 ID 回写 proc_inst_id；proc_inst_id 不再由调用方自填。</p>
 *
 * <p>fail-open：flow 不可用时单据保留 PROCESSING、proc_inst_id 置空，不阻断落库；
 * 最终状态由 flow 回调 {@link PrfApprovalCallbackService} 按 biz_key 原值驱动。</p>
 */
@Service
public class AppealService {

    private static final Logger log = LoggerFactory.getLogger(AppealService.class);

    /** 状态：审批中（创建即提交，服务端强制） */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 状态：已通过（终态） */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 状态：已驳回（终态） */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 流程业务类型（模板 key = bizType） */
    public static final String BIZ_TYPE_APPEAL = "PRF_APPEAL";

    /** biz_key 唯一冲突最大重试次数（雪花 ID 碰撞概率极低，兜底即可） */
    private static final int BIZ_KEY_MAX_RETRY = 3;
    /** 申诉理由进入流程 payload 的摘要截断长度（非敏感摘要，docs/04 §6） */
    private static final int REASON_SUMMARY_LEN = 100;

    private final AppealMapper mapper;
    private final FlowIntegration flowIntegration;

    public AppealService(AppealMapper mapper, FlowIntegration flowIntegration) {
        this.mapper = mapper;
        this.flowIntegration = flowIntegration;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public AppealResponse get(Long id) {
        Appeal e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Appeal 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询记录列表。 */
    public PageResult<AppealResponse> list(long page, long size) {
        IPage<Appeal> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<AppealResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /**
     * 创建申诉单：服务端生成 biz_key（唯一冲突重试）、强制 PROCESSING 并发起 PRF_APPEAL 流程，
     * 成功回写 proc_inst_id；flow 不可用时保留 PROCESSING、proc_inst_id 置空（fail-open）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AppealResponse create(AppealCreateRequest req) {
        Appeal e = new Appeal();
        e.setEmpId(req.empId());
        e.setCycleId(req.cycleId());
        e.setReason(req.reason());
        e.setStatus(STATUS_PROCESSING);
        insertWithBizKeyRetry(e);

        String procInstId = flowIntegration.start(BIZ_TYPE_APPEAL, e.getBizKey(), payload(e));
        if (procInstId != null) {
            e.setProcInstId(procInstId);
            mapper.updateById(e);
        } else {
            log.warn("申诉单已落库但流程未发起（fail-open）: bizKey={}", e.getBizKey());
        }
        return toResponse(e);
    }

    /**
     * 更新申诉单：仅允许改 empId/cycleId/reason；biz_key/status/proc_inst_id 旁路已封死，
     * 状态只由审批回调迁移。终态（APPROVED/REJECTED）单据不可再改，防止对已决申诉重发流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public AppealResponse update(Long id, AppealUpdateRequest req) {
        Appeal e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Appeal 不存在");
        }
        if (STATUS_APPROVED.equals(e.getStatus()) || STATUS_REJECTED.equals(e.getStatus())) {
            throw new BizException(ErrorCode.PRF_ERROR, "申诉单已终态（" + e.getStatus() + "），不可修改或重新发起");
        }
        e.setEmpId(req.empId());
        e.setCycleId(req.cycleId());
        e.setReason(req.reason());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 按主键删除记录，不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Appeal 不存在");
        }
    }

    /** 落库：biz_key 服务端生成，撞 uk_prf_appeal_biz 唯一键时重新生成并重试。 */
    private void insertWithBizKeyRetry(Appeal e) {
        DataIntegrityViolationException last = null;
        for (int i = 0; i < BIZ_KEY_MAX_RETRY; i++) {
            e.setBizKey(nextBizKey());
            try {
                mapper.insert(e);
                return;
            } catch (DataIntegrityViolationException ex) {
                last = ex;
                log.warn("申诉单 biz_key 唯一冲突，重新生成重试: attempt={}, bizKey={}", i + 1, e.getBizKey());
            }
        }
        throw new BizException(ErrorCode.PRF_ERROR, "申诉单号生成冲突，请重试");
    }

    /** 业务单号：prf_appeal 前缀 + 雪花 ID（uk(tenant_id, biz_key) 兜底幂等，回调按原值定位）。 */
    private String nextBizKey() {
        return "prf_appeal:" + IdWorker.getId();
    }

    /** 流程 payload：考核对象/申诉理由摘要（非敏感，flow 不落库仅作路由）。 */
    private Map<String, Object> payload(Appeal e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empId", e.getEmpId());
        payload.put("cycleId", e.getCycleId());
        payload.put("reasonSummary", summarize(e.getReason()));
        return payload;
    }

    private String summarize(String reason) {
        if (reason == null || reason.length() <= REASON_SUMMARY_LEN) {
            return reason;
        }
        return reason.substring(0, REASON_SUMMARY_LEN) + "...";
    }

    private AppealResponse toResponse(Appeal e) {
        return new AppealResponse(e.getId(), e.getEmpId(), e.getCycleId(), e.getBizKey(), e.getReason(), e.getStatus(), e.getProcInstId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
