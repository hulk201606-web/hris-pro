package com.hris.common.security;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PermAspect 单元测试（垂直越权强制拦截，docs/08 安全基线）。
 *
 * <p>切面是「后端强制」而非前端隐藏：perms 缺失/不含所需权限点时必须抛 FORBIDDEN(20403)
 * 且不执行目标方法。perms 来自网关验签后的 X-User-Perms 头，由 AuthContextFilter 还原。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermAspectTest {

    @Mock
    private ProceedingJoinPoint pjp;
    @Mock
    private RequirePerm requirePerm;

    private PermAspect newAspect() {
        return new PermAspect();
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    @DisplayName("perms 含所需权限 → 放行并返回目标方法结果")
    void proceedsWhenPermitted() throws Throwable {
        AuthContextHolder.setPerms(Set.of("emp:employee:read", "cmp:payslip:read"));
        when(requirePerm.value()).thenReturn("emp:employee:read");
        when(pjp.proceed()).thenReturn("ok");

        assertEquals("ok", newAspect().around(pjp, requirePerm));
        verify(pjp).proceed();
    }

    @Test
    @DisplayName("perms 不含所需权限 → FORBIDDEN(20403) 且不执行目标方法")
    void throwsWhenPermMissing() throws Throwable {
        AuthContextHolder.setPerms(Set.of("emp:employee:read"));
        when(requirePerm.value()).thenReturn("cmp:salary:read");

        BizException e = assertThrows(BizException.class, () -> newAspect().around(pjp, requirePerm));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), e.getCode());
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("perms 为 null（未认证/头缺失）→ FORBIDDEN")
    void throwsWhenPermsNull() throws Throwable {
        when(requirePerm.value()).thenReturn("emp:employee:read");

        BizException e = assertThrows(BizException.class, () -> newAspect().around(pjp, requirePerm));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), e.getCode());
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("perms 为空集合 → FORBIDDEN")
    void throwsWhenPermsEmpty() throws Throwable {
        AuthContextHolder.setPerms(Set.of());
        when(requirePerm.value()).thenReturn("emp:employee:read");

        BizException e = assertThrows(BizException.class, () -> newAspect().around(pjp, requirePerm));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), e.getCode());
    }
}
