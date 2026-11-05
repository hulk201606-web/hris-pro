package com.hris.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.employee.dto.ProfileDtos.ProfileEducationDto;
import com.hris.employee.dto.ProfileDtos.ProfileEmergencyDto;
import com.hris.employee.dto.ProfileDtos.ProfileFamilyDto;
import com.hris.employee.dto.ProfileDtos.ProfileWorkDto;
import com.hris.employee.entity.ProfileEducation;
import com.hris.employee.entity.ProfileEmergency;
import com.hris.employee.entity.ProfileFamily;
import com.hris.employee.entity.ProfileWork;
import com.hris.employee.mapper.ProfileEducationMapper;
import com.hris.employee.mapper.ProfileEmergencyMapper;
import com.hris.employee.mapper.ProfileFamilyMapper;
import com.hris.employee.mapper.ProfileWorkMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分域扩展档案（EMP-F-001：学历/工作经历/家庭成员/紧急联系人）。replace 为逻辑删除旧行 + 插入新行（保留审计）。
 */
@Service
public class ProfileService {

    /** 分域档案快照。 */
    public record ProfileSnapshot(List<ProfileEducationDto> educations, List<ProfileWorkDto> works,
                                  List<ProfileFamilyDto> families, List<ProfileEmergencyDto> emergencies) {
    }

    private final ProfileEducationMapper educationMapper;
    private final ProfileWorkMapper workMapper;
    private final ProfileFamilyMapper familyMapper;
    private final ProfileEmergencyMapper emergencyMapper;

    public ProfileService(ProfileEducationMapper educationMapper, ProfileWorkMapper workMapper,
                          ProfileFamilyMapper familyMapper, ProfileEmergencyMapper emergencyMapper) {
        this.educationMapper = educationMapper;
        this.workMapper = workMapper;
        this.familyMapper = familyMapper;
        this.emergencyMapper = emergencyMapper;
    }

    public ProfileSnapshot load(Long employeeId) {
        return new ProfileSnapshot(
                educationMapper.selectList(wrapEdu(employeeId)).stream().map(this::toEdu).toList(),
                workMapper.selectList(wrapWork(employeeId)).stream().map(this::toWork).toList(),
                familyMapper.selectList(wrapFamily(employeeId)).stream().map(this::toFamily).toList(),
                emergencyMapper.selectList(wrapEmergency(employeeId)).stream().map(this::toEmergency).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(Long employeeId, List<ProfileEducationDto> educations, List<ProfileWorkDto> works,
                        List<ProfileFamilyDto> families, List<ProfileEmergencyDto> emergencies) {
        replaceEducations(employeeId, educations);
        replaceWorks(employeeId, works);
        replaceFamilies(employeeId, families);
        replaceEmergencies(employeeId, emergencies);
    }

    /**
     * 教育经历整体替换（EMP-F-001 分签独立维护）。null 不动（保留旧数据），空列表清空该域。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceEducations(Long employeeId, List<ProfileEducationDto> list) {
        if (list == null) {
            return;
        }
        educationMapper.delete(wrapEdu(employeeId));
        for (ProfileEducationDto d : list) {
            ProfileEducation e = new ProfileEducation();
            e.setEmployeeId(employeeId);
            e.setSchool(d.school());
            e.setMajor(d.major());
            e.setDegree(d.degree());
            e.setStartDate(d.startDate());
            e.setEndDate(d.endDate());
            educationMapper.insert(e);
        }
    }

    /** 工作经历整体替换。 */
    @Transactional(rollbackFor = Exception.class)
    public void replaceWorks(Long employeeId, List<ProfileWorkDto> list) {
        if (list == null) {
            return;
        }
        workMapper.delete(wrapWork(employeeId));
        for (ProfileWorkDto d : list) {
            ProfileWork e = new ProfileWork();
            e.setEmployeeId(employeeId);
            e.setCompany(d.company());
            e.setPositionName(d.positionName());
            e.setStartDate(d.startDate());
            e.setEndDate(d.endDate());
            e.setDescription(d.description());
            workMapper.insert(e);
        }
    }

    /** 家庭成员整体替换。 */
    @Transactional(rollbackFor = Exception.class)
    public void replaceFamilies(Long employeeId, List<ProfileFamilyDto> list) {
        if (list == null) {
            return;
        }
        familyMapper.delete(wrapFamily(employeeId));
        for (ProfileFamilyDto d : list) {
            ProfileFamily e = new ProfileFamily();
            e.setEmployeeId(employeeId);
            e.setName(d.name());
            e.setRelation(d.relation());
            e.setPhone(d.phone());
            e.setCompany(d.company());
            familyMapper.insert(e);
        }
    }

    /** 紧急联系人整体替换。 */
    @Transactional(rollbackFor = Exception.class)
    public void replaceEmergencies(Long employeeId, List<ProfileEmergencyDto> list) {
        if (list == null) {
            return;
        }
        emergencyMapper.delete(wrapEmergency(employeeId));
        for (ProfileEmergencyDto d : list) {
            ProfileEmergency e = new ProfileEmergency();
            e.setEmployeeId(employeeId);
            e.setName(d.name());
            e.setRelation(d.relation());
            e.setPhone(d.phone());
            e.setAddress(d.address());
            emergencyMapper.insert(e);
        }
    }

    private LambdaQueryWrapper<ProfileEducation> wrapEdu(Long employeeId) {
        return new LambdaQueryWrapper<ProfileEducation>().eq(ProfileEducation::getEmployeeId, employeeId);
    }

    private LambdaQueryWrapper<ProfileWork> wrapWork(Long employeeId) {
        return new LambdaQueryWrapper<ProfileWork>().eq(ProfileWork::getEmployeeId, employeeId);
    }

    private LambdaQueryWrapper<ProfileFamily> wrapFamily(Long employeeId) {
        return new LambdaQueryWrapper<ProfileFamily>().eq(ProfileFamily::getEmployeeId, employeeId);
    }

    private LambdaQueryWrapper<ProfileEmergency> wrapEmergency(Long employeeId) {
        return new LambdaQueryWrapper<ProfileEmergency>().eq(ProfileEmergency::getEmployeeId, employeeId);
    }

    private ProfileEducationDto toEdu(ProfileEducation e) {
        return new ProfileEducationDto(e.getId(), e.getSchool(), e.getMajor(), e.getDegree(),
                e.getStartDate(), e.getEndDate());
    }

    private ProfileWorkDto toWork(ProfileWork e) {
        return new ProfileWorkDto(e.getId(), e.getCompany(), e.getPositionName(),
                e.getStartDate(), e.getEndDate(), e.getDescription());
    }

    private ProfileFamilyDto toFamily(ProfileFamily e) {
        return new ProfileFamilyDto(e.getId(), e.getName(), e.getRelation(), e.getPhone(), e.getCompany());
    }

    private ProfileEmergencyDto toEmergency(ProfileEmergency e) {
        return new ProfileEmergencyDto(e.getId(), e.getName(), e.getRelation(), e.getPhone(), e.getAddress());
    }
}
