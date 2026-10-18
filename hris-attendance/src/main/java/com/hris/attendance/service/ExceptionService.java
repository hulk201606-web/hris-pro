package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.attendance.dto.ExceptionDtos.ExceptionBatchHandleRequest;
import com.hris.attendance.dto.ExceptionDtos.ExceptionHandleRequest;
import com.hris.attendance.dto.ExceptionDtos.ExceptionHandleResult;
import com.hris.attendance.dto.ExceptionDtos.ExceptionResponse;
import com.hris.attendance.dto.PageResult;
import com.hris.attendance.entity.AttendanceException;
import com.hris.attendance.mapper.AttendanceExceptionMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 异常处理工作台（ATT-F-020）。按部门/日期列出异常并支持批量认定或豁免。
 *
 * <p>说明：月结前置校验要求「未决异常单 = 0」，因此 RESOLVE / IGNORE 两个动作
 * 都会把异常从 OPEN 迁出（IGNORE 表示豁免不纳入考核，仍留有痕迹）。</p>
 */
@Service
public class ExceptionService {

    /** 异常状态：待处理 */
    public static final String STATUS_OPEN = "OPEN";
    /** 异常状态：已认定 */
    public static final String STATUS_RESOLVED = "RESOLVED";
    /** 异常状态：已豁免 */
    public static final String STATUS_IGNORED = "IGNORED";

    private final AttendanceExceptionMapper exceptionMapper;

    public ExceptionService(AttendanceExceptionMapper exceptionMapper) {
        this.exceptionMapper = exceptionMapper;
    }

    /** 异常分页查询（按员工 / 日期区间 / 类型 / 状态）。 */
    public PageResult<ExceptionResponse> list(Long empId, LocalDate from, LocalDate to,
                                              String exceptionType, String status,
                                              long page, long size) {
        Page<AttendanceException> p = new Page<>(page, size);
        LambdaQueryWrapper<AttendanceException> qw = new LambdaQueryWrapper<>();
        if (empId != null) {
            qw.eq(AttendanceException::getEmpId, empId);
        }
        if (from != null) {
            qw.ge(AttendanceException::getWorkDate, from);
        }
        if (to != null) {
            qw.le(AttendanceException::getWorkDate, to);
        }
        if (exceptionType != null && !exceptionType.isBlank()) {
            qw.eq(AttendanceException::getExceptionType, exceptionType.trim().toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            qw.eq(AttendanceException::getStatus, status.trim().toUpperCase());
        }
        qw.orderByDesc(AttendanceException::getWorkDate).orderByAsc(AttendanceException::getId);

        Page<AttendanceException> result = exceptionMapper.selectPage(p, qw);
        List<ExceptionResponse> records = result.getRecords().stream()
                .map(this::toResponse).toList();
        return new PageResult<>(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 单条异常处理。 */
    @Transactional(rollbackFor = Exception.class)
    public ExceptionResponse handle(Long id, ExceptionHandleRequest req) {
        AttendanceException e = require(id);
        e.setStatus(resolveStatus(req.action()));
        e.setDescription(mergeRemark(e.getDescription(), req.remark()));
        if (exceptionMapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 批量处理：单条失败不影响其余，返回失败 ID 便于前端提示重试。 */
    @Transactional(rollbackFor = Exception.class)
    public ExceptionHandleResult batchHandle(ExceptionBatchHandleRequest req) {
        String status = resolveStatus(req.action());
        List<Long> failedIds = new ArrayList<>();
        int success = 0;
        for (Long id : req.ids()) {
            AttendanceException e = exceptionMapper.selectById(id);
            if (e == null) {
                failedIds.add(id);
                continue;
            }
            e.setStatus(status);
            e.setDescription(mergeRemark(e.getDescription(), req.remark()));
            if (exceptionMapper.updateById(e) == 0) {
                throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
            }
            success++;
        }
        return new ExceptionHandleResult(req.ids().size(), success, failedIds);
    }

    /** 统计某租户在某日期区间内的未决异常数（月结前置校验）。 */
    public long countOpen(LocalDate from, LocalDate to) {
        Long cnt = exceptionMapper.selectCount(new LambdaQueryWrapper<AttendanceException>()
                .ge(AttendanceException::getWorkDate, from)
                .le(AttendanceException::getWorkDate, to)
                .eq(AttendanceException::getStatus, STATUS_OPEN));
        return cnt == null ? 0L : cnt;
    }

    private AttendanceException require(Long id) {
        AttendanceException e = exceptionMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考勤异常不存在");
        }
        return e;
    }

    private String resolveStatus(String action) {
        String a = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return switch (a) {
            case "IGNORE" -> STATUS_IGNORED;
            case "RESOLVE" -> STATUS_RESOLVED;
            default -> throw new BizException(ErrorCode.PARAM_INVALID,
                    "处理动作必须为 RESOLVE 或 IGNORE");
        };
    }

    private String mergeRemark(String origin, String remark) {
        if (remark == null || remark.isBlank()) {
            return origin;
        }
        return origin == null || origin.isBlank() ? remark : origin + "；" + remark;
    }

    private ExceptionResponse toResponse(AttendanceException e) {
        return new ExceptionResponse(e.getId(), e.getEmpId(), e.getWorkDate(),
                e.getExceptionType(), e.getStatus(), e.getDescription(), e.getCreatedAt());
    }
}
