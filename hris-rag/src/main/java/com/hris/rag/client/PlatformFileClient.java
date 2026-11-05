package com.hris.rag.client;

import com.hris.common.api.R;
import com.hris.common.security.AuthContextFilter;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 平台文件服务 Feign 客户端（批 12 #28：按 fileId 取简历附件）。
 *
 * <p>契约：GET /api/v1/plt/files/download?id= → data={fileId, downloadUrl, degraded, watermark}。
 * 简历只存 {@code rcu_resume.file_id}，字节在 MinIO，故抽取前须先取预签名下载地址。</p>
 *
 * <p><b>身份透传</b>：平台该端点受 {@code plt:file:read} 保护，而 rag 的内部调用（来自 recruit）
 * 不带 Authorization。故本客户端专属配置 {@link IdentityForwarding} 把当前请求的身份头
 * （X-User-Id / X-User-Perms / X-User-Roles / X-Tenant-Id）原样转发，实现「以调用者身份取件」；
 * 无请求上下文（如定时任务）时不转发，平台按无权限拒绝，由调用方降级。</p>
 */
@FeignClient(name = "hris-platform", path = "/api/v1/plt/files", contextId = "ragPlatformFileClient",
        configuration = PlatformFileClient.IdentityForwarding.class)
public interface PlatformFileClient {

    @GetMapping("/download")
    R<Map<String, Object>> download(@RequestParam("id") Long fileId);

    /**
     * 身份头透传配置（刻意不加 @Configuration：仅作用于本客户端，避免被组件扫描成全局拦截器）。
     */
    class IdentityForwarding {

        private static final String[] HEADERS = {
                AuthContextFilter.HEADER_USER_ID,
                AuthContextFilter.HEADER_PERMS,
                AuthContextFilter.HEADER_ROLES,
                AuthContextFilter.HEADER_TENANT_ID,
                "Authorization"
        };

        @org.springframework.context.annotation.Bean
        public RequestInterceptor platformIdentityInterceptor() {
            return template -> {
                if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
                    return;
                }
                HttpServletRequest req = attrs.getRequest();
                for (String header : HEADERS) {
                    String value = req.getHeader(header);
                    if (value != null && !value.isBlank()) {
                        template.header(header, value);
                    }
                }
            };
        }
    }
}
