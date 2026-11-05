package com.hris.employee.client;

import com.hris.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 薪酬域自助取数（SELF-F-001 工资条卡片）。
 *
 * <p>工资条为密域：调用失败或无 cmp 权限时由聚合服务置 null 并标 source=fallback，
 * 绝不返回明文金额（响应仅有 netPayMask）。</p>
 */
@FeignClient(name = "hris-compensation", contextId = "empCmpSelfClient",
        configuration = SelfFeignConfig.class)
public interface CmpSelfClient {

    /** 我的工资条（自助端点，按当前用户过滤）。 */
    @GetMapping("/api/v1/cmp/payslips/me")
    R<List<PayslipMe>> myPayslips();

    /** 工资条（仅掩码）。 */
    record PayslipMe(Long id, String periodCode, String grossMask, String netPayMask) {
    }
}
