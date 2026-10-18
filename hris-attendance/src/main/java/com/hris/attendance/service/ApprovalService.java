package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.attendance.dto.ApprovalDtos.ApprovalCallbackRequest;
import com.hris.attendance.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.attendance.dto.FixDtos.FixActionRequest;
import com.hris.attendance.dto.FixDtos.FixResponse;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveActionRequest;
import com.hris.attendance.dto.LeaveRequestDtos.LeaveResponse;
import com.hris.attendance.dto.OvertimeDtos.OvertimeActionRequest;
import com.hris.attendance.dto.OvertimeDtos.OvertimeResponse;
import com.hris.attendance.dto.TripDtos.TripActionRequest;
import com.hris.attendance.dto.TripDtos.TripResponse;
import com.hris.attendance.entity.FixRequest;
import com.hris.attendance.entity.LeaveRequest;
import com.hris.attendance.entity.OvertimeRequest;
import com.hris.attendance.entity.TripRequest;
import com.hris.attendance.mapper.FixRequestMapper;
import com.hris.attendance.mapper.LeaveRequestMapper;
import com.hris.attendance.mapper.OvertimeRequestMapper;
import com.hris.attendance.mapper.TripRequestMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 审批回调分发（flow → hris-attendance）。
 *
 * <p>按 biz_type 路由到对应单据服务，以 biz_key 定位单据；回调幂等——
 * 单据不处于审批中时返回 changed=false 而不报错，便于 flow 侧安全重试。</p>
 */
@Service
public class ApprovalService {

    private final LeaveRequestMapper leaveRequestMapper;
    private final OvertimeRequestMapper overtimeRequestMapper;
    private final TripRequestMapper tripRequestMapper;
    private final FixRequestMapper fixRequestMapper;
    private final LeaveRequestService leaveRequestService;
    private final OvertimeService overtimeService;
    private final TripService tripService;
    private final FixRequestService fixRequestService;

    public ApprovalService(LeaveRequestMapper leaveRequestMapper,
                           OvertimeRequestMapper overtimeRequestMapper,
                           TripRequestMapper tripRequestMapper,
                           FixRequestMapper fixRequestMapper,
                           LeaveRequestService leaveRequestService,
                           OvertimeService overtimeService,
                           TripService tripService,
                           FixRequestService fixRequestService) {
        this.leaveRequestMapper = leaveRequestMapper;
        this.overtimeRequestMapper = overtimeRequestMapper;
        this.tripRequestMapper = tripRequestMapper;
        this.fixRequestMapper = fixRequestMapper;
        this.leaveRequestService = leaveRequestService;
        this.overtimeService = overtimeService;
        this.tripService = tripService;
        this.fixRequestService = fixRequestService;
    }

    /**
     * 审批结果回调。
     *
     * @param req 含 biz_type / biz_key / approved / proc_inst_id / remark
     * @return 回调处理结果；changed=false 表示重复回调或单据非审批中
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalCallbackResponse callback(ApprovalCallbackRequest req) {
        String bizType = req.bizType().trim().toUpperCase(Locale.ROOT);
        String remark = req.remark() == null ? "" : req.remark();
        boolean approved = Boolean.TRUE.equals(req.approved());
        return switch (bizType) {
            case LeaveRequestService.BIZ_TYPE_LEAVE -> applyLeave(req, approved, remark);
            case OvertimeService.BIZ_TYPE_OVERTIME -> applyOvertime(req, approved, remark);
            case TripService.BIZ_TYPE_TRIP -> applyTrip(req, approved, remark);
            case FixRequestService.BIZ_TYPE_FIX -> applyFix(req, approved, remark);
            default -> throw new BizException(ErrorCode.PARAM_INVALID,
                    "不支持的业务类型：" + bizType);
        };
    }

    private ApprovalCallbackResponse applyLeave(ApprovalCallbackRequest req, boolean approved,
                                                String remark) {
        LeaveRequest leave = locateLeave(req.bizKey());
        if (!LeaveRequestService.STATUS_PROCESSING.equals(leave.getStatus())) {
            return new ApprovalCallbackResponse(LeaveRequestService.BIZ_TYPE_LEAVE, req.bizKey(),
                    leave.getStatus(), false);
        }
        LeaveResponse updated = approved
                ? leaveRequestService.approve(leave.getId(), new LeaveActionRequest(remark))
                : leaveRequestService.reject(leave.getId(), new LeaveActionRequest(remark));
        return new ApprovalCallbackResponse(LeaveRequestService.BIZ_TYPE_LEAVE, req.bizKey(),
                updated.status(), true);
    }

    private ApprovalCallbackResponse applyOvertime(ApprovalCallbackRequest req, boolean approved,
                                                   String remark) {
        OvertimeRequest ot = locateOvertime(req.bizKey());
        if (!OvertimeService.STATUS_PROCESSING.equals(ot.getStatus())) {
            return new ApprovalCallbackResponse(OvertimeService.BIZ_TYPE_OVERTIME, req.bizKey(),
                    ot.getStatus(), false);
        }
        OvertimeResponse updated = approved
                ? overtimeService.approve(ot.getId(), new OvertimeActionRequest(remark))
                : overtimeService.reject(ot.getId(), new OvertimeActionRequest(remark));
        return new ApprovalCallbackResponse(OvertimeService.BIZ_TYPE_OVERTIME, req.bizKey(),
                updated.status(), true);
    }

    private ApprovalCallbackResponse applyTrip(ApprovalCallbackRequest req, boolean approved,
                                               String remark) {
        TripRequest trip = locateTrip(req.bizKey());
        if (!TripService.STATUS_PROCESSING.equals(trip.getStatus())) {
            return new ApprovalCallbackResponse(TripService.BIZ_TYPE_TRIP, req.bizKey(),
                    trip.getStatus(), false);
        }
        TripResponse updated = approved
                ? tripService.approve(trip.getId(), new TripActionRequest(remark))
                : tripService.reject(trip.getId(), new TripActionRequest(remark));
        return new ApprovalCallbackResponse(TripService.BIZ_TYPE_TRIP, req.bizKey(),
                updated.status(), true);
    }

    private ApprovalCallbackResponse applyFix(ApprovalCallbackRequest req, boolean approved,
                                              String remark) {
        FixRequest fix = locateFix(req.bizKey());
        if (!FixRequestService.STATUS_PROCESSING.equals(fix.getStatus())) {
            return new ApprovalCallbackResponse(FixRequestService.BIZ_TYPE_FIX, req.bizKey(),
                fix.getStatus(), false);
        }
        FixResponse updated = approved
                ? fixRequestService.approve(fix.getId(), new FixActionRequest(remark))
                : fixRequestService.reject(fix.getId(), new FixActionRequest(remark));
        return new ApprovalCallbackResponse(FixRequestService.BIZ_TYPE_FIX, req.bizKey(),
                updated.status(), true);
    }

    private LeaveRequest locateLeave(String bizKey) {
        LeaveRequest leave = leaveRequestMapper.selectOne(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getBizKey, bizKey)
                .last("LIMIT 1"));
        if (leave == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "请假单不存在");
        }
        return leave;
    }

    private OvertimeRequest locateOvertime(String bizKey) {
        OvertimeRequest ot = overtimeRequestMapper.selectOne(
                new LambdaQueryWrapper<OvertimeRequest>()
                        .eq(OvertimeRequest::getBizKey, bizKey)
                        .last("LIMIT 1"));
        if (ot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "加班单不存在");
        }
        return ot;
    }

    private TripRequest locateTrip(String bizKey) {
        TripRequest trip = tripRequestMapper.selectOne(new LambdaQueryWrapper<TripRequest>()
                .eq(TripRequest::getBizKey, bizKey)
                .last("LIMIT 1"));
        if (trip == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "出差单不存在");
        }
        return trip;
    }

    private FixRequest locateFix(String bizKey) {
        FixRequest fix = fixRequestMapper.selectOne(new LambdaQueryWrapper<FixRequest>()
                .eq(FixRequest::getBizKey, bizKey)
                .last("LIMIT 1"));
        if (fix == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "补卡单不存在");
        }
        return fix;
    }
}
