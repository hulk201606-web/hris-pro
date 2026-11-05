package com.hris.employee.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 组织域字典查询（SELF-F-001 部门/岗位名称回填）。
 *
 * <p>org 当前无「按 ID 取名称」的内部端点，这里复用公开字典端点并透传权限头；
 * 无 org:dept:read / org:position:read 时 fail-open，首页仅回 ID 对应的 null 名称。</p>
 */
@FeignClient(name = "hris-org", contextId = "empOrgSelfClient",
        configuration = SelfFeignConfig.class)
public interface OrgSelfClient {

    /** 部门扁平列表（tree=false）。 */
    @GetMapping("/api/v1/org/depts")
    R<List<Map<String, Object>>> depts(@RequestParam("tree") boolean tree);

    /** 岗位字典。 */
    @GetMapping("/api/v1/org/positions")
    R<List<Map<String, Object>>> positions();
}
