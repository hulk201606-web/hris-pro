package com.hris.common.audit;

import com.hris.common.trace.TraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 审计切面。MVP 阶段先输出结构化审计日志；Phase 3 接入 platform 服务后，
 * 改为通过 RocketMQ 异步写入 audit_log（docs/08 §5：登录/薪酬/导出类强一致同步落库）。
 */
@Aspect
@Component
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class AuditLogAspect {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            success = false;
            throw t;
        } finally {
            audit.info("audit|traceId={}|tenantId={}|userId={}|module={}|op={}|ok={}|costMs={}",
                    TraceContext.getTraceId(),
                    TraceContext.getTenantId(),
                    TraceContext.getUserId(),
                    auditLog.module(),
                    auditLog.value(),
                    success,
                    System.currentTimeMillis() - start);
        }
    }
}
