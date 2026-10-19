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

    /**
     * 环绕 @AuditLog 标注的方法：记录开始时间，proceed 执行业务，finally 块输出结构化审计日志。
     *
     * <p>日志字段：traceId（贯穿链路）、tenantId（租户隔离 ID）、userId（操作人）、
     * module/op（操作模块与描述）、ok（成功/失败）、costMs（耗时毫秒）。
     * 业务异常在 catch 中标记 success=false 后原样 rethrow，不改变业务行为。</p>
     *
     * @param pjp       切点
     * @param auditLog  注解实例（含 value=操作描述、module=业务模块）
     * @return 业务方法原返回值
     * @throws Throwable 业务方法抛出的原始异常
     */
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
