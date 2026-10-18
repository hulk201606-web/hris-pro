package com.hris.compensation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 算薪取数预览 DTO（跨域组合：员工主档 + 考勤月结/日明细，docs/07）。
 */
public final class PayrollPreviewDtos {

    private PayrollPreviewDtos() {
    }

    /** 算薪预览请求：周期编码 + 参与员工 ID 列表。 */
    public record PayrollPreviewRequest(String periodCode, List<Long> empIds) {
    }

    /** 算薪预览单行：员工 + 当期考勤汇总（跨域取数结果）。 */
    public record PayrollPreviewLine(Long empId, String empNo, String name, Long deptId,
                                    String attPeriodStatus, BigDecimal overtimeHours,
                                    BigDecimal leaveHours, Long periodEmployeeCount) {
    }
}
