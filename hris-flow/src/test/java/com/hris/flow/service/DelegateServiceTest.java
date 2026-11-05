package com.hris.flow.service;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.flow.dto.DelegateDtos.CreateRequest;
import com.hris.flow.dto.DelegateDtos.MineResponse;
import com.hris.flow.entity.FlowExtDelegate;
import com.hris.flow.mapper.FlowExtDelegateMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 委托代理服务单元测试（FLW-F-009）：创建守卫、重叠拒绝、提前结束权限。
 */
@ExtendWith(MockitoExtension.class)
class DelegateServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

    @Mock
    FlowExtDelegateMapper delegateMapper;

    private DelegateService service;

    @BeforeEach
    void setUp() {
        service = new DelegateService(delegateMapper);
        TraceContext.setTenantId(100001L);
        TraceContext.setUserId(5L);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    private CreateRequest req(Long fromUser, Long toUser, String procDefKey,
                              LocalDateTime startAt, LocalDateTime endAt) {
        return new CreateRequest(fromUser, toUser, procDefKey, startAt, endAt, "年假离场");
    }

    @Test
    @DisplayName("创建守卫：to_user 等于 from_user → PARAM_INVALID")
    void createShouldRejectSelfDelegate() {
        BizException e = assertThrows(BizException.class,
                () -> service.create(req(5L, 5L, null, NOW, NOW.plusDays(1))));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(delegateMapper, never()).insert(any(FlowExtDelegate.class));
    }

    @Test
    @DisplayName("创建守卫：end_at 不晚于 start_at → PARAM_INVALID")
    void createShouldRejectBadRange() {
        BizException e = assertThrows(BizException.class,
                () -> service.create(req(5L, 7L, null, NOW, NOW)));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
    }

    @Test
    @DisplayName("重叠拒绝：同 from_user 同流程范围 ACTIVE 期相交 → PARAM_INVALID")
    void createShouldRejectOverlapping() {
        FlowExtDelegate existing = new FlowExtDelegate();
        existing.setFromUser(5L);
        existing.setToUser(7L);
        existing.setProcDefKey(null);
        existing.setStartAt(NOW.minusDays(1));
        existing.setEndAt(NOW.plusDays(2));
        existing.setStatus(DelegateService.STATUS_ACTIVE);
        when(delegateMapper.selectList(any())).thenReturn(List.of(existing));

        BizException e = assertThrows(BizException.class,
                () -> service.create(req(5L, 8L, "ATT_LEAVE", NOW.plusDays(1), NOW.plusDays(3))));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), e.getCode());
        verify(delegateMapper, never()).insert(any(FlowExtDelegate.class));
    }

    @Test
    @DisplayName("创建成功：缺省 from_user=当前登录人，status=ACTIVE，procDefKey 空串归一为 NULL")
    void createShouldSucceed() {
        when(delegateMapper.selectList(any())).thenReturn(List.of());

        var resp = service.create(req(null, 7L, "  ", NOW, NOW.plusDays(1)));

        assertEquals(5L, resp.fromUser());
        assertEquals(7L, resp.toUser());
        assertEquals(DelegateService.STATUS_ACTIVE, resp.status());
        ArgumentCaptor<FlowExtDelegate> captor = ArgumentCaptor.forClass(FlowExtDelegate.class);
        verify(delegateMapper).insert(captor.capture());
        assertEquals(DelegateService.STATUS_ACTIVE, captor.getValue().getStatus());
        assertEquals(5L, captor.getValue().getFromUser());
        assertEquals(7L, captor.getValue().getToUser());
    }

    @Test
    @DisplayName("mine：status=ACTIVE 过滤我创建的与委托给我的两组列表")
    void mineShouldFilterByStatus() {
        FlowExtDelegate mine = new FlowExtDelegate();
        mine.setId(1L);
        mine.setFromUser(5L);
        mine.setToUser(7L);
        mine.setStatus(DelegateService.STATUS_ACTIVE);
        FlowExtDelegate received = new FlowExtDelegate();
        received.setId(2L);
        received.setFromUser(9L);
        received.setToUser(5L);
        received.setStatus(DelegateService.STATUS_ENDED);
        // 两次查询（我创建的/委托给我的）分别返回不同列表
        when(delegateMapper.selectList(any())).thenReturn(List.of(mine), List.of(received));

        MineResponse resp = service.mine("ACTIVE");

        assertEquals(1, resp.created().size());
        assertEquals(0, resp.received().size());
    }

    @Test
    @DisplayName("end：非发起人提前结束 → 30423 拒绝")
    void endShouldRejectNonOwner() {
        FlowExtDelegate d = new FlowExtDelegate();
        d.setId(1L);
        d.setFromUser(9L);
        d.setToUser(5L);
        d.setStatus(DelegateService.STATUS_ACTIVE);
        d.setTenantId(100001L);
        when(delegateMapper.selectById(1L)).thenReturn(d);

        BizException e = assertThrows(BizException.class, () -> service.end(1L));
        assertEquals(ErrorCode.FLOW_NO_PERMISSION.getCode(), e.getCode());
    }

    @Test
    @DisplayName("end：发起人提前结束 → 状态置 ENDED")
    void endShouldSucceedForOwner() {
        FlowExtDelegate d = new FlowExtDelegate();
        d.setId(1L);
        d.setFromUser(5L);
        d.setToUser(7L);
        d.setStatus(DelegateService.STATUS_ACTIVE);
        d.setTenantId(100001L);
        when(delegateMapper.selectById(1L)).thenReturn(d);

        service.end(1L);

        ArgumentCaptor<FlowExtDelegate> captor = ArgumentCaptor.forClass(FlowExtDelegate.class);
        verify(delegateMapper).updateById(captor.capture());
        assertEquals(DelegateService.STATUS_ENDED, captor.getValue().getStatus());
    }
}
