package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.client.EmpClient;
import com.hris.attendance.dto.FixDtos.FixActionRequest;
import com.hris.attendance.dto.FixDtos.FixCreateRequest;
import com.hris.attendance.dto.FixDtos.FixResponse;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.entity.FixRequest;
import com.hris.attendance.entity.PunchRecord;
import com.hris.attendance.mapper.FixRequestMapper;
import com.hris.attendance.mapper.PunchRecordMapper;
import com.hris.attendance.util.CodeUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 补卡申请（ATT-F-019）。月度次数受控，审批通过后补写打卡记录。
 *
 * <p>状态机：DRAFT → PROCESSING → APPROVED / REJECTED / CANCELLED。
 * 月度上限见 {@link #MONTHLY_LIMIT}，按 work_date 所在自然月统计
 * PROCESSING + APPROVED 两种占用态。</p>
 *
 * <p>审批身份（B-6）：直批路径校验审批人是申请人直属上级（经 emp 内部端点
 * profile-brief 查现行雇佣关系汇报线）；flow 回调路径跳过——审批人已由 flow 侧
 * AssigneeResolver 按 payload.supervisorEmpId 解析。</p>
 */
@Service
public class FixRequestService {

    /** 流程业务类型 */
    public static final String BIZ_TYPE_FIX = "ATT_FIX";

    /** 月度补卡次数上限 */
    public static final int MONTHLY_LIMIT = 3;

    /** 状态：审批中 */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 状态：已通过 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 状态：已撤销 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final FixRequestMapper fixMapper;
    private final PunchRecordMapper punchMapper;
    private final FlowIntegration flowIntegration;
    private final EmpClient empClient;

    public FixRequestService(FixRequestMapper fixMapper, PunchRecordMapper punchMapper,
                             FlowIntegration flowIntegration, EmpClient empClient) {
        this.fixMapper = fixMapper;
        this.punchMapper = punchMapper;
        this.flowIntegration = flowIntegration;
        this.empClient = empClient;
    }

    /** 提交补卡单：校验月度次数后落单并发起审批。 */
    @Transactional(rollbackFor = Exception.class)
    public FixResponse create(FixCreateRequest req) {
        String type = req.type() == null ? "" : req.type().trim().toUpperCase(Locale.ROOT);
        if (!"IN".equals(type) && !"OUT".equals(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "补卡类型必须为 IN 或 OUT");
        }
        ensureWithinMonthlyLimit(req.empId(), req.workDate());

        FixRequest fix = new FixRequest();
        fix.setId(IdWorker.getId());
        fix.setEmpId(req.empId());
        fix.setBizKey(CodeUtil.bizKey("att_fix"));
        fix.setWorkDate(req.workDate());
        fix.setFixTime(req.fixTime());
        fix.setType(type);
        fix.setReason(req.reason());
        fix.setStatus(STATUS_PROCESSING);
        fix.setProcInstId(flowIntegration.start(BIZ_TYPE_FIX, fix.getBizKey(), payload(fix)));
        fixMapper.insert(fix);
        return toResponse(fix);
    }

    /** 审批通过：状态置 APPROVED 并补写一条打卡记录。approverEmpId 传 null 表示 flow 回调路径。 */
    @Transactional(rollbackFor = Exception.class)
    public FixResponse approve(Long id, FixActionRequest req) {
        return approve(id, req, null);
    }

    /** 审批通过（直批入口，校验审批人是申请人直属上级）。 */
    @Transactional(rollbackFor = Exception.class)
    public FixResponse approve(Long id, FixActionRequest req, Long approverEmpId) {
        FixRequest fix = require(id);
        ensureProcessing(fix);
        ensureSupervisor(fix, approverEmpId);
        fix.setStatus(STATUS_APPROVED);
        fix.setReason(appendRemark(fix.getReason(), req.remark()));
        if (fixMapper.updateById(fix) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        writeBackPunch(fix);
        return toResponse(fix);
    }

    /** 审批驳回。approverEmpId 传 null 表示 flow 回调路径。 */
    @Transactional(rollbackFor = Exception.class)
    public FixResponse reject(Long id, FixActionRequest req) {
        return reject(id, req, null);
    }

    /** 审批驳回（直批入口，校验审批人是申请人直属上级）。 */
    @Transactional(rollbackFor = Exception.class)
    public FixResponse reject(Long id, FixActionRequest req, Long approverEmpId) {
        FixRequest fix = require(id);
        ensureProcessing(fix);
        ensureSupervisor(fix, approverEmpId);
        fix.setStatus(STATUS_REJECTED);
        fix.setReason(appendRemark(fix.getReason(), req.remark()));
        if (fixMapper.updateById(fix) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(fix);
    }

    /** 单个补卡单。 */
    public FixResponse get(Long id) {
        return toResponse(require(id));
    }

    /** 补卡单分页查询（按员工 / 状态 / 日期区间）。 */
    public PageResult<FixResponse> list(Long empId, String status, LocalDate from, LocalDate to,
                                        long page, long size) {
        Page<FixRequest> p = new Page<>(page, size);
        LambdaQueryWrapper<FixRequest> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(FixRequest::getEmpId, empId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(FixRequest::getStatus, status.trim().toUpperCase());
        }
        if (from != null) {
            qw.ge(FixRequest::getWorkDate, from);
        }
        if (to != null) {
            qw.le(FixRequest::getWorkDate, to);
        }
        qw.orderByDesc(FixRequest::getWorkDate).orderByDesc(FixRequest::getId);

        Page<FixRequest> result = fixMapper.selectPage(p, qw);
        List<FixResponse> records = result.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 月度次数校验：达到上限即拒绝，避免无限补卡。 */
    private void ensureWithinMonthlyLimit(Long empId, LocalDate workDate) {
        LocalDate monthStart = workDate.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        Long used = fixMapper.selectCount(new LambdaQueryWrapper<FixRequest>()
                .eq(FixRequest::getEmpId, empId)
                .ge(FixRequest::getWorkDate, monthStart)
                .le(FixRequest::getWorkDate, monthEnd)
                .in(FixRequest::getStatus, List.of(STATUS_PROCESSING, STATUS_APPROVED)));
        if (used != null && used >= MONTHLY_LIMIT) {
            throw new BizException(ErrorCode.ATT_ERROR,
                    "本月补卡次数已达上限 " + MONTHLY_LIMIT + " 次");
        }
    }

    /** 审批通过后补写打卡记录（幂等：同员工同时刻已存在则跳过）。 */
    private void writeBackPunch(FixRequest fix) {
        Long cnt = punchMapper.selectCount(new LambdaQueryWrapper<PunchRecord>()
                .eq(PunchRecord::getEmpId, fix.getEmpId())
                .eq(PunchRecord::getPunchTime, fix.getFixTime()));
        if (cnt != null && cnt > 0) {
            return;
        }
        PunchRecord record = new PunchRecord();
        record.setId(IdWorker.getId());
        record.setEmpId(fix.getEmpId());
        record.setPunchTime(fix.getFixTime());
        record.setType(fix.getType());
        record.setSource("FIX");
        record.setStatus(PunchService.STATUS_NORMAL);
        punchMapper.insert(record);
    }

    /**
     * 补卡审批身份校验（B-6）：直批路径（approverEmpId 非空）必须由申请人直属上级审批。
     * 直属上级经 emp 内部端点查询；查不到汇报线（服务不可达/员工无上级）时明确拒绝，
     * 不放宽到任意持有 att:fix:approve 权限者。
     */
    private void ensureSupervisor(FixRequest fix, Long approverEmpId) {
        if (approverEmpId == null) {
            return;
        }
        Long supervisorEmpId = empClient.findSupervisorEmpId(fix.getEmpId());
        if (supervisorEmpId == null) {
            throw new BizException(ErrorCode.ATT_ERROR,
                    "无法确认申请人直属上级，补卡单不可直批");
        }
        if (!supervisorEmpId.equals(approverEmpId)) {
            throw new BizException(ErrorCode.ATT_ERROR, "仅申请人直属上级可审批补卡单");
        }
    }

    private Map<String, Object> payload(FixRequest fix) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("empId", fix.getEmpId());
        payload.put("workDate", String.valueOf(fix.getWorkDate()));
        payload.put("fixTime", String.valueOf(fix.getFixTime()));
        payload.put("type", fix.getType());
        return payload;
    }

    private void ensureProcessing(FixRequest fix) {
        if (!STATUS_PROCESSING.equals(fix.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR, "单据不处于审批中，不可重复审批");
        }
    }

    private FixRequest require(Long id) {
        FixRequest fix = fixMapper.selectById(id);
        if (fix == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "补卡单不存在");
        }
        return fix;
    }

    private String appendRemark(String origin, String remark) {
        if (remark == null || remark.isBlank()) {
            return origin;
        }
        return origin == null || origin.isBlank() ? remark : origin + "；" + remark;
    }

    private FixResponse toResponse(FixRequest fix) {
        return new FixResponse(fix.getId(), fix.getEmpId(), fix.getBizKey(), fix.getWorkDate(),
                fix.getFixTime(), fix.getType(), fix.getReason(), fix.getStatus(),
                fix.getProcInstId(), fix.getCreatedAt(), fix.getUpdatedAt());
    }
}
