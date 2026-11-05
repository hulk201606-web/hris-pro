package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.dto.OutOfficeDtos.OutOfficeCreateRequest;
import com.hris.attendance.dto.OutOfficeDtos.OutOfficeResponse;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.entity.OutOffice;
import com.hris.attendance.mapper.OutOfficeMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 外出/居家办公申请（ATT-F-018）。
 *
 * <p>架构决策（与 F-017 出差保持一致的直批范式）：审批不走 Flowable，由
 * att:out-office:write 权限点在端点层直接批准/驳回——外出/居家单时效短、
 * 无薪酬联动流程，同步落状态即可；创建也不发起流程实例（区别于出差单的
 * 「发起但可降级」），单据即最终事实。</p>
 *
 * <p>状态机：PROCESSING → APPROVED / REJECTED（终态不可再审）。
 * 时间守卫：end &gt; start；同员工区间与既有 PROCESSING/APPROVED 单重叠则拒绝。</p>
 */
@Service
public class OutOfficeService {

    /** 类型：外出 */
    public static final String TYPE_OUT = "OUT";
    /** 类型：居家办公 */
    public static final String TYPE_WFH = "WFH";

    /** 状态：审批中 */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 状态：已通过 */
    public static final String STATUS_APPROVED = "APPROVED";
    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "REJECTED";

    private final OutOfficeMapper outOfficeMapper;

    public OutOfficeService(OutOfficeMapper outOfficeMapper) {
        this.outOfficeMapper = outOfficeMapper;
    }

    /** 自助创建：empId 由控制器按当前用户解析后传入。 */
    @Transactional(rollbackFor = Exception.class)
    public OutOfficeResponse create(Long empId, OutOfficeCreateRequest req) {
        String type = req.type() == null ? "" : req.type().trim().toUpperCase(Locale.ROOT);
        if (!TYPE_OUT.equals(type) && !TYPE_WFH.equals(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "类型只能是 OUT（外出）或 WFH（居家办公）");
        }
        if (!req.endTime().isAfter(req.startTime())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        ensureNoOverlap(empId, req.startTime(), req.endTime());

        OutOffice e = new OutOffice();
        e.setId(IdWorker.getId());
        e.setEmpId(empId);
        e.setType(type);
        e.setStartTime(req.startTime());
        e.setEndTime(req.endTime());
        e.setReason(req.reason());
        e.setStatus(STATUS_PROCESSING);
        outOfficeMapper.insert(e);
        return toResponse(e);
    }

    /** 我的申请（按提交时间倒序）。 */
    public List<OutOfficeResponse> listMine(Long empId) {
        return outOfficeMapper.selectList(new LambdaQueryWrapper<OutOffice>()
                        .eq(OutOffice::getEmpId, empId)
                        .orderByDesc(OutOffice::getId)).stream()
                .map(this::toResponse).toList();
    }

    /** 管理端分页列表（按员工 / 状态过滤）。 */
    public PageResult<OutOfficeResponse> list(Long empId, String status, long page, long size) {
        Page<OutOffice> p = new Page<>(page, size);
        LambdaQueryWrapper<OutOffice> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(OutOffice::getEmpId, empId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(OutOffice::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        qw.orderByDesc(OutOffice::getId);

        Page<OutOffice> result = outOfficeMapper.selectPage(p, qw);
        List<OutOfficeResponse> records = result.getRecords().stream()
                .map(this::toResponse).toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 审批通过：终态守卫 + 回填审批人。 */
    @Transactional(rollbackFor = Exception.class)
    public OutOfficeResponse approve(Long id, Long approverId) {
        return decide(id, approverId, STATUS_APPROVED);
    }

    /** 审批驳回：终态守卫 + 回填审批人。 */
    @Transactional(rollbackFor = Exception.class)
    public OutOfficeResponse reject(Long id, Long approverId) {
        return decide(id, approverId, STATUS_REJECTED);
    }

    private OutOfficeResponse decide(Long id, Long approverId, String targetStatus) {
        OutOffice e = require(id);
        ensureProcessing(e);
        e.setStatus(targetStatus);
        e.setApproverId(approverId);
        if (outOfficeMapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 重叠校验：与本人 PROCESSING/APPROVED 单区间相交即拒绝（半开区间判定）。 */
    private void ensureNoOverlap(Long empId, LocalDateTime start, LocalDateTime end) {
        List<OutOffice> overlaps = outOfficeMapper.selectList(new LambdaQueryWrapper<OutOffice>()
                .eq(OutOffice::getEmpId, empId)
                .in(OutOffice::getStatus, List.of(STATUS_PROCESSING, STATUS_APPROVED))
                .lt(OutOffice::getStartTime, end)
                .gt(OutOffice::getEndTime, start));
        if (!overlaps.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "时间区间与既有外出/居家办公申请重叠，请调整后重试");
        }
    }

    private void ensureProcessing(OutOffice e) {
        if (!STATUS_PROCESSING.equals(e.getStatus())) {
            throw new BizException(ErrorCode.ATT_ERROR, "单据不处于审批中，不可重复审批");
        }
    }

    private OutOffice require(Long id) {
        OutOffice e = outOfficeMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "外出/居家办公申请不存在");
        }
        return e;
    }

    private OutOfficeResponse toResponse(OutOffice e) {
        return new OutOfficeResponse(e.getId(), e.getEmpId(), e.getType(),
                e.getStartTime(), e.getEndTime(), e.getReason(), e.getStatus(),
                e.getApproverId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
