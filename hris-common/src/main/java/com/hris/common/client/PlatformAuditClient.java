package com.hris.common.client;

import com.hris.common.audit.AuditRecord;
import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * platform 审计落库内部客户端（docs/19 §8.7-2）。
 *
 * <p>POST /internal/plt/audit 为内部端点：网关 InternalBanWebFilter 封禁 /internal/** 公网访问，
 * 仅容器网络内服务间直连可达。各 Servlet 服务在自己的 FeignConfig 中把
 * {@code com.hris.common.client} 加入 @EnableFeignClients 扫描即可接入；common 的
 * AuditSinkConfig 会自动装配经此客户端落库的 AuditSink。</p>
 */
@FeignClient(name = "hris-platform", path = "/internal/plt", contextId = "platformAuditClient")
public interface PlatformAuditClient {

    /** 审计追加（追加式写入，platform 侧不校验 @RequirePerm，靠网络边界防伪造）。 */
    @PostMapping("/audit")
    R<Void> append(@RequestBody AuditRecord record);
}
