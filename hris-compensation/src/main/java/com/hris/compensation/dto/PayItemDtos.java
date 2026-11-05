package com.hris.compensation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 薪资项目相关 DTO（域：薪酬，分层：DTO/接口契约）。
 * 按 pay_group 维度定义，含类别（EARNING/DEDUCTION/TAX/NET）与 Aviator 公式（CMP-F-021 沙箱）。
 */
public final class PayItemDtos {

    private PayItemDtos() {
    }

    /** 创建请求。 */
    public record PayItemCreateRequest(
            @NotNull(message = "payGroupId 不能为空") Long payGroupId,
            @NotBlank(message = "name 不能为空") String name,
            @NotBlank(message = "code 不能为空") String code,
            @NotNull(message = "category 不能为空")
            @Pattern(regexp = "EARNING|DEDUCTION|TAX|NET", message = "category 必须为 EARNING/DEDUCTION/TAX/NET")
            String category,
            Integer seq,
            @Size(max = 512, message = "formula 最长 512 字符") String formula,
            @NotNull(message = "status 不能为空") Integer status) {
    }

    /** 更新请求。 */
    public record PayItemUpdateRequest(
            String name,
            @Pattern(regexp = "EARNING|DEDUCTION|TAX|NET", message = "category 必须为 EARNING/DEDUCTION/TAX/NET")
            String category,
            Integer seq,
            @Size(max = 512, message = "formula 最长 512 字符") String formula,
            Integer status) {
    }

    /** 响应。 */
    public record PayItemResponse(Long id, Long payGroupId, String name, String code, String category,
                                  Integer seq, String formula, Integer status,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 按 payGroupId 批量响应（算薪端配置视图）。 */
    public record PayItemListResponse(List<PayItemResponse> items) {
    }
}
