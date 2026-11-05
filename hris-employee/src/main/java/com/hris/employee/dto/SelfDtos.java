package com.hris.employee.dto;

import java.util.List;

/**
 * 员工自助 DTO（SELF-F-001 首页聚合 / SELF-F-002 我的信息 / EMP-F-021 信息变更）。
 */
public final class SelfDtos {

    private SelfDtos() {
    }

    /**
     * 自助信息变更请求（非关键字段直改生效）。
     *
     * <p>certNo/certType/bankAccount/hireType 为关键字段：出现非空值即拒绝，
     * 引导走 EMP_PROFILE_CHG 流程（前端据 message 中的模板键跳转）。</p>
     */
    public record SelfProfileUpdateRequest(
            String name,
            String mobile,
            String email,
            String address,
            String emergencyContact,
            String emergencyPhone,
            String certNo,
            String certType,
            String bankAccount,
            String hireType) {
    }

    /** 我的信息（脱敏视图，证件/手机仅掩码）。 */
    public record SelfProfileResponse(
            Long empId,
            String empNo,
            String name,
            String certNoMask,
            String mobileMask,
            String email,
            String address,
            String emergencyContact,
            String emergencyPhone,
            Long deptId,
            Long positionId,
            String hireType) {
    }

    /** 关键字段变更申请（走 EMP_PROFILE_CHG 审批）。 */
    public record CriticalChangeRequest(
            String certNo,
            String certType,
            String bankAccount,
            String hireType,
            String reason) {
    }

    /** 变更申请响应：bizKey 用于前端查询审批进度。 */
    public record CriticalChangeResponse(
            String bizKey,
            String templateKey,
            String procInstId,
            String status) {
    }

    /** 个人资料子项（本地数据，恒为 live）。 */
    public record ProfileSection(String name, String empNo, String deptName, String positionName,
                                 java.time.LocalDate hireDate, String source) {
    }

    /** 今日考勤 + 本月迟到次数。 */
    public record AttendanceSection(String todayStatus, Integer monthLateCount, String source) {
    }

    /** 假期余额项（remainingDays 为 availableQuota 的字符串形态，保留精度）。 */
    public record LeaveBalanceItem(String leaveType, String remainingDays) {
    }

    /** 假期余额子项。 */
    public record LeaveSection(List<LeaveBalanceItem> items, String source) {
    }

    /** 最新工资条（无权或不可用时数据为 null，source=fallback）。 */
    public record PayslipSection(String latestPeriod, String netAmountMasked, String source) {
    }

    /** 流程待办计数。 */
    public record PendingFlowSection(Integer count, String source) {
    }

    /** 自助首页聚合响应；partial=true 表示至少一个子项降级。 */
    public record SelfDashboardResponse(
            ProfileSection profile,
            AttendanceSection attendance,
            LeaveSection leaves,
            PayslipSection payslip,
            PendingFlowSection pendingFlows,
            boolean partial,
            long costMs) {
    }
}
