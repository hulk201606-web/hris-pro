package com.hris.flow.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 组织域 Feign 客户端（预留契约：审批人解析依赖汇报线）。
 *
 * <p>hris-org 已提供 {@code GET /internal/org/reportline/{empId}}（返回上级链 userId 列表）。
 * 服务未就绪时经 {@code AssigneeResolver} 降级为「待指派」，不阻断不丢单（docs/04 §4）。</p>
 */
@FeignClient(name = "hris-org", path = "/internal/org")
public interface OrgClient {

    @GetMapping("/reportline/{empId}")
    R<List<Long>> reportline(@PathVariable("empId") Long empId);
}
