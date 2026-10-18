package com.hris.employee.dto;

import java.time.LocalDate;

/**
 * 候选人预建档内部请求（/internal/emp/pre-create）。
 *
 * <p>由招聘域在 Offer 录用后调用，预创建员工主档（状态=待入职），
 * 仅携带候选人最小信息；部门/岗位/入职办理由后续 EMP 入职流程补全（docs/07 跨域契约）。</p>
 */
public record PreCreateRequest(
        String name,
        String certType,
        String certNo,
        String mobile,
        String email,
        String gender,
        LocalDate birthDate,
        String empNoPrefix) {
}
