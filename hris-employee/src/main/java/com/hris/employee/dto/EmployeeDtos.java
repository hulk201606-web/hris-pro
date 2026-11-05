package com.hris.employee.dto;

import com.hris.employee.dto.ProfileDtos.ProfileEducationDto;
import com.hris.employee.dto.ProfileDtos.ProfileEmergencyDto;
import com.hris.employee.dto.ProfileDtos.ProfileFamilyDto;
import com.hris.employee.dto.ProfileDtos.ProfileWorkDto;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工档案 DTO（建档 / 更新 / 档案详情）。
 */
public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    /** 建档请求（emp_employee 主档 + 分域扩展）。 */
    public record EmployeeCreateRequest(
            @NotBlank(message = "name 不能为空") String name,
            String certType,
            String certNo,
            String mobile,
            String email,
            String gender,
            LocalDate birthDate,
            String empNoPrefix,
            List<ProfileEducationDto> educations,
            List<ProfileWorkDto> works,
            List<ProfileFamilyDto> families,
            List<ProfileEmergencyDto> emergencies) {
    }

    /** 档案更新请求（分组字段权限：证件/手机为密域字段，MVP 预留权限点 emp:profile:sensitive）。 */
    public record EmployeeUpdateRequest(
            String name,
            String email,
            String gender,
            LocalDate birthDate,
            String certType,
            String certNo,
            String mobile,
            List<ProfileEducationDto> educations,
            List<ProfileWorkDto> works,
            List<ProfileFamilyDto> families,
            List<ProfileEmergencyDto> emergencies) {
    }

    /** 当前雇佣关系摘要（档案内嵌）。 */
    public record EmploymentInfo(Long legalEntityId, Long deptId, Long positionId, Long gradeId,
                                 String hireType, Long supervisorEmpId, LocalDate effectiveFrom) {
    }

    /** 档案详情响应。certNo/mobile 仅密域角色填充，默认只回掩码。 */
    public record EmployeeProfileResponse(
            Long id, String empNo, String name, String gender, LocalDate birthDate, String email,
            Integer status, String statusLabel, Integer blacklisted,
            String certNoMask, String mobileMask, String certNo, String mobile,
            EmploymentInfo employment,
            List<ProfileEducationDto> educations,
            List<ProfileWorkDto> works,
            List<ProfileFamilyDto> families,
            List<ProfileEmergencyDto> emergencies) {
    }
    /**
     * 再入职识别结果（EMP-F-029）：按证件号查历史任职记录。
     *
     * 注意：本查询依赖 cert_no_cipher 当前**按明文落库**（未接 ShardingSphere 加密插件）。
     * 若将来改为随机 IV 加密，同值密文不同，等值查询会失效——届时必须增加
     * cert_no_hash 确定性索引列并改由此列查询，不能直接沿用本实现。
     */
    public record RehireCheckResponse(boolean rehire, int historyCount,
                                      List<RehireHistoryItem> histories) {
    }

    /** 一条历史任职记录（含已离职 / 黑名单）。 */
    public record RehireHistoryItem(Long empId, String empNo, String name,
                                    Integer status, String statusLabel, Integer blacklisted) {
    }

    /**
     * 试用期到期提醒项（EMP-F-007 深化，platform PROBATION_REMINDER 调度用）。
     * level 取 D15/D7/D3（剩余天数命中 15/7/3 档），按 daysRemaining 归档。
     */
    public record ProbationReminderItem(
            Long contractId, Long empId, String empNo, String name, Long deptId,
            LocalDate probationEnd, long daysRemaining, String level) {
    }
}
