package com.hris.flow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.DelegateDtos.CreateRequest;
import com.hris.flow.dto.DelegateDtos.DelegateResponse;
import com.hris.flow.dto.DelegateDtos.MineResponse;
import com.hris.flow.entity.FlowExtDelegate;
import com.hris.flow.mapper.FlowExtDelegateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批委托代理服务（FLW-F-009）。
 *
 * <p>核心语义：生效期内（now ∈ [start_at, end_at] 且 ACTIVE）的委托，把 from_user 的
 * 待办任务并入 to_user 的待办列表（proc_def_key 匹配或 NULL 全匹配）；to_user 审批时
 * 留痕 operator=实际操作人 + delegateFrom=委托发起人。</p>
 */
@Service
public class DelegateService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

    private final FlowExtDelegateMapper delegateMapper;

    public DelegateService(FlowExtDelegateMapper delegateMapper) {
        this.delegateMapper = delegateMapper;
    }

    /** 创建委托：to_user≠from_user、end_at>start_at、不与同 from_user 同范围 ACTIVE 期重叠。 */
    @Transactional(rollbackFor = Exception.class)
    public DelegateResponse create(CreateRequest req) {
        Long tenantId = requireTenant();
        Long fromUser = req.fromUser() == null ? TraceContext.getUserId() : req.fromUser();
        if (fromUser == null || fromUser == 0L) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少委托发起人身份");
        }
        if (req.toUser() == null || req.toUser() == 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "toUser 不能为空");
        }
        if (req.toUser().equals(fromUser)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "to_user 不能等于 from_user，不能委托给自己");
        }
        if (req.startAt() == null || req.endAt() == null || !req.endAt().isAfter(req.startAt())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "end_at 必须晚于 start_at");
        }
        String procDefKey = normalizeProcDefKey(req.procDefKey());
        if (hasOverlapping(tenantId, fromUser, procDefKey, req.startAt(), req.endAt())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "与同发起人同流程范围的生效中委托时间重叠");
        }
        FlowExtDelegate d = new FlowExtDelegate();
        d.setFromUser(fromUser);
        d.setToUser(req.toUser());
        d.setProcDefKey(procDefKey);
        d.setStartAt(req.startAt());
        d.setEndAt(req.endAt());
        d.setStatus(STATUS_ACTIVE);
        d.setReason(req.reason());
        delegateMapper.insert(d);
        return toResponse(d);
    }

    /** 我的委托：我创建的 + 委托给我的，status 可选过滤（ACTIVE/ENDED）。 */
    public MineResponse mine(String status) {
        Long tenantId = requireTenant();
        Long uid = requireUser();
        List<FlowExtDelegate> created = delegateMapper.selectList(baseQuery()
                .eq(FlowExtDelegate::getFromUser, uid)
                .orderByDesc(FlowExtDelegate::getStartAt));
        List<FlowExtDelegate> received = delegateMapper.selectList(baseQuery()
                .eq(FlowExtDelegate::getToUser, uid)
                .orderByDesc(FlowExtDelegate::getStartAt));
        String st = normalizeStatus(status);
        return new MineResponse(
                created.stream().filter(d -> matchStatus(d, st)).map(this::toResponse).toList(),
                received.stream().filter(d -> matchStatus(d, st)).map(this::toResponse).toList());
    }

    /** 提前结束委托：仅委托发起人可操作，状态置 ENDED。 */
    @Transactional(rollbackFor = Exception.class)
    public void end(Long id) {
        Long tenantId = requireTenant();
        Long uid = requireUser();
        FlowExtDelegate d = delegateMapper.selectById(id);
        if (d == null || !tenantId.equals(d.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "委托记录不存在");
        }
        if (!uid.equals(d.getFromUser())) {
            throw new BizException(ErrorCode.FLOW_NO_PERMISSION, "仅委托发起人可提前结束委托");
        }
        if (STATUS_ENDED.equals(d.getStatus())) {
            return;
        }
        d.setStatus(STATUS_ENDED);
        delegateMapper.updateById(d);
    }

    /**
     * 生效期内委托给我的记录（to_user=uid 且 ACTIVE 且 now ∈ [start_at, end_at]），
     * 供待办并入与审批留痕判定使用。
     */
    public List<FlowExtDelegate> activeDelegationsTo(Long tenantId, Long toUser, LocalDateTime now) {
        return delegateMapper.selectList(baseQuery()
                .eq(FlowExtDelegate::getToUser, toUser)
                .eq(FlowExtDelegate::getStatus, STATUS_ACTIVE)
                .le(FlowExtDelegate::getStartAt, now)
                .ge(FlowExtDelegate::getEndAt, now));
    }

    /**
     * 审批留痕判定：当前操作人是否经由委托代办 from_user 的任务。
     * 匹配条件：存在生效委托 to_user=操作人 且 from_user=任务当前 assignee，
     * 且委托范围（proc_def_key）为 NULL 或与任务流程定义 Key 一致；否则返回 null。
     */
    public Long resolveDelegateFrom(Long operator, Long tenantId, String taskAssignee, String procDefKey) {
        if (operator == null || taskAssignee == null || taskAssignee.isBlank()) {
            return null;
        }
        for (FlowExtDelegate d : activeDelegationsTo(tenantId, operator, LocalDateTime.now())) {
            if (d.getFromUser() != null && taskAssignee.equals(String.valueOf(d.getFromUser()))
                    && scopeMatches(d.getProcDefKey(), procDefKey)) {
                return d.getFromUser();
            }
        }
        return null;
    }

    /** 委托范围匹配：NULL=全部流程，否则要求与流程定义 Key 相等。 */
    private boolean scopeMatches(String delegateProcKey, String procDefKey) {
        return delegateProcKey == null || delegateProcKey.isBlank()
                || delegateProcKey.equals(procDefKey);
    }

    /**
     * 重叠判定：同租户同 from_user 且状态 ACTIVE，时间区间相交
     * （existing.start_at &lt; new.end_at 且 new.start_at &lt; existing.end_at），
     * 流程范围相交（任一为 NULL 全匹配，或两者相等）即视为重叠。
     */
    private boolean hasOverlapping(Long tenantId, Long fromUser, String procDefKey,
                                   LocalDateTime startAt, LocalDateTime endAt) {
        List<FlowExtDelegate> actives = delegateMapper.selectList(baseQuery()
                .eq(FlowExtDelegate::getFromUser, fromUser)
                .eq(FlowExtDelegate::getStatus, STATUS_ACTIVE)
                .lt(FlowExtDelegate::getStartAt, endAt)
                .gt(FlowExtDelegate::getEndAt, startAt));
        return actives.stream().anyMatch(d ->
                d.getProcDefKey() == null || procDefKey == null
                        || d.getProcDefKey().equals(procDefKey));
    }

    /** 租户隔离由 MyBatis-Plus 租户插件自动注入。 */
    private LambdaQueryWrapper<FlowExtDelegate> baseQuery() {
        return new LambdaQueryWrapper<FlowExtDelegate>();
    }

    private boolean matchStatus(FlowExtDelegate d, String status) {
        return status == null || status.equals(d.getStatus());
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String st = status.trim().toUpperCase();
        if (!STATUS_ACTIVE.equals(st) && !STATUS_ENDED.equals(st)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 仅支持 ACTIVE/ENDED");
        }
        return st;
    }

    private String normalizeProcDefKey(String procDefKey) {
        return procDefKey == null || procDefKey.isBlank() ? null : procDefKey.trim();
    }

    private DelegateResponse toResponse(FlowExtDelegate d) {
        return new DelegateResponse(d.getId(), d.getFromUser(), d.getToUser(), d.getProcDefKey(),
                d.getStartAt(), d.getEndAt(), d.getStatus(), d.getReason());
    }

    private Long requireTenant() {
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null || tenantId == 0L) {
            throw new BizException(ErrorCode.TENANT_INVALID);
        }
        return tenantId;
    }

    private Long requireUser() {
        Long userId = TraceContext.getUserId();
        if (userId == null || userId == 0L) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少用户身份");
        }
        return userId;
    }
}
