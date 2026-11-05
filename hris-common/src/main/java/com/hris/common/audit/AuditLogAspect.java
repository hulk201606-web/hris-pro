package com.hris.common.audit;

import com.hris.common.trace.TraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计切面（docs/08 §5、docs/19 §8.7-2）。环绕 @AuditLog 标注的方法：
 *
 * <ol>
 *   <li>容器内有 {@link AuditSink} 实现（platform 本地直连 / 其余服务经 Feign 调
 *       POST /internal/plt/audit）则同步追加落库——落库失败降级写 logback，绝不阻断业务；</li>
 *   <li>无 Sink 实现时保持原行为：输出结构化审计日志到 logback。</li>
 * </ol>
 *
 * <p>记录字段：tenantId/userId 取自 TraceContext，module/op 取自注解，ok/costMs/traceId/IP
 * 一并携带（ok/costMs/traceId 收敛进 detail JSON）。业务异常标记 ok=false 后原样 rethrow。</p>
 */
@Aspect
@Component
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class AuditLogAspect {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final ObjectProvider<AuditSink> sinkProvider;

    public AuditLogAspect(ObjectProvider<AuditSink> sinkProvider) {
        this.sinkProvider = sinkProvider;
    }

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
            long costMs = System.currentTimeMillis() - start;
            emit(auditLog, success, costMs);
        }
    }

    /** 落库优先（失败降级 logback），无 Sink 则 logback。任何异常不得外溢。 */
    private void emit(AuditLog auditLog, boolean success, long costMs) {
        try {
            AuditSink sink = sinkProvider.getIfAvailable();
            if (sink != null) {
                try {
                    sink.append(buildRecord(auditLog, success, costMs));
                    return;
                } catch (Throwable t) {
                    audit.warn("audit sink append failed, fallback to logback|op={}|err={}",
                            auditLog.value(), t.getMessage());
                }
            }
            logback(auditLog, success, costMs);
        } catch (Throwable t) {
            audit.warn("audit emit failed|op={}|err={}", auditLog.value(), t.getMessage());
        }
    }

    private AuditRecord buildRecord(AuditLog auditLog, boolean success, long costMs) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("traceId", TraceContext.getTraceId());
        detail.put("ok", success);
        detail.put("costMs", costMs);
        return new AuditRecord(TraceContext.getTenantId(), TraceContext.getUserId(),
                auditLog.module(), auditLog.value(), success, costMs, currentIp(), detail);
    }

    private void logback(AuditLog auditLog, boolean success, long costMs) {
        audit.info("audit|traceId={}|tenantId={}|userId={}|module={}|op={}|ok={}|costMs={}",
                TraceContext.getTraceId(),
                TraceContext.getTenantId(),
                TraceContext.getUserId(),
                auditLog.module(),
                auditLog.value(),
                success,
                costMs);
    }

    /** 取客户端 IP（Servlet 环境才有；WebFlux/异步线程返回 null，由 Sink 端兜底）。 */
    private String currentIp() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest().getRemoteAddr();
            }
        } catch (Throwable ignore) {
            // 非 Servlet 上下文（如 gateway/定时任务）无请求信息
        }
        return null;
    }
}
