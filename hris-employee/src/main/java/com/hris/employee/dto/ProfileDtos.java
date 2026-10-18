package com.hris.employee.dto;

import java.time.LocalDate;

/**
 * 分域档案 DTO（EMP-F-001 基本/学历/工作经历/家庭成员/紧急联系人分签）。
 */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    /** 教育经历。 */
    public record ProfileEducationDto(Long id, String school, String major, String degree,
                                      LocalDate startDate, LocalDate endDate) {
    }

    /** 工作经历。 */
    public record ProfileWorkDto(Long id, String company, String positionName,
                                 LocalDate startDate, LocalDate endDate, String description) {
    }

    /** 家庭成员。 */
    public record ProfileFamilyDto(Long id, String name, String relation, String phone, String company) {
    }

    /** 紧急联系人。 */
    public record ProfileEmergencyDto(Long id, String name, String relation, String phone, String address) {
    }
}
