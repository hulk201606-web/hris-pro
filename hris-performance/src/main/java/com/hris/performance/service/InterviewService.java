package com.hris.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.performance.dto.InterviewDtos.InterviewCreateRequest;
import com.hris.performance.dto.InterviewDtos.InterviewResponse;
import com.hris.performance.dto.InterviewDtos.InterviewUpdateRequest;
import com.hris.performance.dto.InterviewDtos.PipResultRequest;
import com.hris.performance.entity.Interview;
import com.hris.performance.mapper.CycleMapper;
import com.hris.performance.mapper.InterviewMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Interview 服务（PRF-F-008：绩效面谈记录与 PIP 跟踪）。
 *
 * <p>服务端强制语义：round 缺省 1 且同周期同员工唯一（uk_prf_interview 兜底）；
 * conclusion 仅允许 IMPROVE/KEEP/PROMOTE/PIP，conclusion=PIP 时 pip_start/end 必填
 * 且 end&gt;start，pip_status 缺省置 ONGOING；非 PIP 结论 pip 字段恒清空。
 * pip_status 迁移只经 {@link #pipResult(Long, PipResultRequest)}，且仅 ONGOING 可迁移。</p>
 */
@Service
public class InterviewService {

    /** 结论：改进 */
    public static final String CONCLUSION_IMPROVE = "IMPROVE";
    /** 结论：维持 */
    public static final String CONCLUSION_KEEP = "KEEP";
    /** 结论：晋升建议 */
    public static final String CONCLUSION_PROMOTE = "PROMOTE";
    /** 结论：绩效改善计划 */
    public static final String CONCLUSION_PIP = "PIP";

    /** PIP 状态：进行中 */
    public static final String PIP_ONGOING = "ONGOING";
    /** PIP 状态：通过（终态） */
    public static final String PIP_PASSED = "PASSED";
    /** PIP 状态：未通过（终态） */
    public static final String PIP_FAILED = "FAILED";

    private final InterviewMapper mapper;
    private final CycleMapper cycleMapper;

    public InterviewService(InterviewMapper mapper, CycleMapper cycleMapper) {
        this.mapper = mapper;
        this.cycleMapper = cycleMapper;
    }

    /** 按主键查询单条记录，不存在抛 NOT_FOUND。 */
    public InterviewResponse get(Long id) {
        Interview e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Interview 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询：cycleId/empId 双可选过滤。 */
    public PageResult<InterviewResponse> list(Long cycleId, Long empId, long page, long size) {
        LambdaQueryWrapper<Interview> wrapper = new LambdaQueryWrapper<>();
        if (cycleId != null) {
            wrapper.eq(Interview::getCycleId, cycleId);
        }
        if (empId != null) {
            wrapper.eq(Interview::getEmpId, empId);
        }
        wrapper.orderByDesc(Interview::getInterviewDate);
        IPage<Interview> pg = mapper.selectPage(new Page<>(page, size), wrapper);
        List<InterviewResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 自助视图：本人面谈记录（empId 由网关透传的当前用户解析，self 语义）。 */
    public PageResult<InterviewResponse> listMine(Long empId, long page, long size) {
        return list(null, empId, page, size);
    }

    /** 创建面谈记录：校验 cycle 存在、conclusion 合法、PIP 必填项、round 唯一。 */
    @Transactional(rollbackFor = Exception.class)
    public InterviewResponse create(InterviewCreateRequest req) {
        ensureCycleExists(req.cycleId());
        ensureConclusion(req.conclusion());
        if (CONCLUSION_PIP.equals(req.conclusion())) {
            ensurePipDates(req.pipStartDate(), req.pipEndDate());
        }

        Interview e = new Interview();
        e.setCycleId(req.cycleId());
        e.setEmpId(req.empId());
        e.setRound(req.round() == null ? 1 : req.round());
        e.setInterviewDate(req.interviewDate());
        e.setInterviewerEmpId(req.interviewerEmpId());
        e.setSummary(req.summary());
        e.setConclusion(req.conclusion());
        applyPipFields(e, CONCLUSION_PIP.equals(req.conclusion()), req.pipStartDate(), req.pipEndDate());

        ensureRoundUnique(e.getCycleId(), e.getEmpId(), e.getRound(), null);
        try {
            mapper.insert(e);
        } catch (DataIntegrityViolationException ex) {
            throw new BizException(ErrorCode.PRF_ERROR, "同周期同员工的面谈轮次已存在: round=" + e.getRound());
        }
        return toResponse(e);
    }

    /**
     * 更新面谈记录：基础字段与结论可改，重新走 PIP 校验与 round 唯一校验；
     * pip_status 旁路封死——conclusion 变化时按状态机重置，迁移只经 pip-result。
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewResponse update(Long id, InterviewUpdateRequest req) {
        Interview e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Interview 不存在");
        }
        Long cycleId = req.cycleId() != null ? req.cycleId() : e.getCycleId();
        Long empId = req.empId() != null ? req.empId() : e.getEmpId();
        Integer round = req.round() != null ? req.round() : e.getRound();
        String conclusion = req.conclusion() != null ? req.conclusion() : e.getConclusion();
        ensureCycleExists(cycleId);
        ensureConclusion(conclusion);
        if (CONCLUSION_PIP.equals(conclusion)) {
            LocalDatePair pip = pipDatesOf(req, e);
            ensurePipDates(pip.start(), pip.end());
        }

        e.setCycleId(cycleId);
        e.setEmpId(empId);
        e.setRound(round);
        if (req.interviewDate() != null) {
            e.setInterviewDate(req.interviewDate());
        }
        if (req.interviewerEmpId() != null) {
            e.setInterviewerEmpId(req.interviewerEmpId());
        }
        if (req.summary() != null) {
            e.setSummary(req.summary());
        }
        e.setConclusion(conclusion);
        boolean isPip = CONCLUSION_PIP.equals(conclusion);
        LocalDatePair pip = pipDatesOf(req, e);
        applyPipFields(e, isPip, pip.start(), pip.end());

        ensureRoundUnique(cycleId, empId, round, id);
        try {
            if (mapper.updateById(e) == 0) {
                throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new BizException(ErrorCode.PRF_ERROR, "同周期同员工的面谈轮次已存在: round=" + round);
        }
        return toResponse(e);
    }

    /**
     * PIP 结果登记：仅 conclusion=PIP 且 pip_status=ONGOING 可迁移为 PASSED/FAILED（终态）。
     */
    @Transactional(rollbackFor = Exception.class)
    public InterviewResponse pipResult(Long id, PipResultRequest req) {
        Interview e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Interview 不存在");
        }
        if (!CONCLUSION_PIP.equals(e.getConclusion())) {
            throw new BizException(ErrorCode.PRF_ERROR, "该面谈结论非 PIP，无 PIP 状态可登记");
        }
        if (!PIP_ONGOING.equals(e.getPipStatus())) {
            throw new BizException(ErrorCode.PRF_ERROR, "仅 PIP 状态为 ONGOING 可登记结果，当前: " + e.getPipStatus());
        }
        if (!PIP_PASSED.equals(req.pipStatus()) && !PIP_FAILED.equals(req.pipStatus())) {
            throw new BizException(ErrorCode.PRF_ERROR, "pipStatus 仅允许 PASSED/FAILED");
        }
        e.setPipStatus(req.pipStatus());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    private void ensureCycleExists(Long cycleId) {
        if (cycleMapper.selectById(cycleId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Cycle 不存在: " + cycleId);
        }
    }

    private void ensureConclusion(String conclusion) {
        if (!CONCLUSION_IMPROVE.equals(conclusion) && !CONCLUSION_KEEP.equals(conclusion)
                && !CONCLUSION_PROMOTE.equals(conclusion) && !CONCLUSION_PIP.equals(conclusion)) {
            throw new BizException(ErrorCode.PRF_ERROR, "conclusion 仅允许 IMPROVE/KEEP/PROMOTE/PIP");
        }
    }

    private void ensurePipDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start == null || end == null) {
            throw new BizException(ErrorCode.PRF_ERROR, "conclusion=PIP 时 pipStartDate/pipEndDate 必填");
        }
        if (!end.isAfter(start)) {
            throw new BizException(ErrorCode.PRF_ERROR, "pipEndDate 必须大于 pipStartDate");
        }
    }

    /** 状态机轻实现：PIP 结论缺省 ONGOING；非 PIP 结论 pip 字段恒清空。 */
    private void applyPipFields(Interview e, boolean isPip, java.time.LocalDate start, java.time.LocalDate end) {
        if (isPip) {
            e.setPipStartDate(start);
            e.setPipEndDate(end);
            if (e.getPipStatus() == null) {
                e.setPipStatus(PIP_ONGOING);
            }
        } else {
            e.setPipStartDate(null);
            e.setPipEndDate(null);
            e.setPipStatus(null);
        }
    }

    private LocalDatePair pipDatesOf(InterviewUpdateRequest req, Interview existing) {
        java.time.LocalDate start = req.pipStartDate() != null ? req.pipStartDate() : existing.getPipStartDate();
        java.time.LocalDate end = req.pipEndDate() != null ? req.pipEndDate() : existing.getPipEndDate();
        return new LocalDatePair(start, end);
    }

    /** round 唯一校验（租户插件自动隔离 tenant_id），excludeId 用于更新排除自身。 */
    private void ensureRoundUnique(Long cycleId, Long empId, Integer round, Long excludeId) {
        LambdaQueryWrapper<Interview> wrapper = new LambdaQueryWrapper<Interview>()
                .eq(Interview::getCycleId, cycleId)
                .eq(Interview::getEmpId, empId)
                .eq(Interview::getRound, round);
        if (excludeId != null) {
            wrapper.ne(Interview::getId, excludeId);
        }
        if (mapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PRF_ERROR, "同周期同员工的面谈轮次已存在: round=" + round);
        }
    }

    private InterviewResponse toResponse(Interview e) {
        return new InterviewResponse(e.getId(), e.getCycleId(), e.getEmpId(), e.getRound(),
                e.getInterviewDate(), e.getInterviewerEmpId(), e.getSummary(), e.getConclusion(),
                e.getPipStartDate(), e.getPipEndDate(), e.getPipStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private record LocalDatePair(java.time.LocalDate start, java.time.LocalDate end) {
    }
}
