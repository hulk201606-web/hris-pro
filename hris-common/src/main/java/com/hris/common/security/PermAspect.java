package com.hris.common.security;

import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 权限校验切面。拦截 @RequirePerm 标注的方法，校验当前请求用户 perms。
 *
 * <p>perms 由 {@link AuthContextFilter} 在请求入口还原（来自网关验签后的 X-User-Perms 头）。
 * perms 为空或不包含所需权限点时抛 20403，实现后端强制垂直越权拦截（非前端隐藏）。</p>
 */
@Aspect
@Component
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class PermAspect {

    @Around("@annotation(requirePerm)")
    public Object around(ProceedingJoinPoint pjp, RequirePerm requirePerm) throws Throwable {
        Set<String> perms = AuthContextHolder.getPerms();
        if (perms == null || !perms.contains(requirePerm.value())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限");
        }
        return pjp.proceed();
    }
}
