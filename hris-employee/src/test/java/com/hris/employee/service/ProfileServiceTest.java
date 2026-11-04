package com.hris.employee.service;

import com.hris.employee.dto.ProfileDtos.ProfileEducationDto;
import com.hris.employee.entity.ProfileEducation;
import com.hris.employee.mapper.ProfileEducationMapper;
import com.hris.employee.mapper.ProfileEmergencyMapper;
import com.hris.employee.mapper.ProfileFamilyMapper;
import com.hris.employee.mapper.ProfileWorkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ProfileService 单元测试 —— 分域扩展档案（学历/工作/家庭/紧急联系人）。
 *
 * <p>业务逻辑集中在 replace 的「逻辑删除旧行 + 插入新行」语义：null 列表不动（保留旧数据），
 * 空列表只删不插（清空该域），非空列表删后逐条插入并回填 employeeId。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileEducationMapper educationMapper;
    @Mock
    private ProfileWorkMapper workMapper;
    @Mock
    private ProfileFamilyMapper familyMapper;
    @Mock
    private ProfileEmergencyMapper emergencyMapper;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(educationMapper, workMapper, familyMapper, emergencyMapper);
    }

    @Nested
    @DisplayName("replace：逻辑删除旧行 + 插入新行")
    class Replace {

        @Test
        @DisplayName("全部列表为 null → 各域均不删除不插入（保留旧数据）")
        void nullListsDoNothing() {
            service.replace(100L, null, null, null, null);

            verify(educationMapper, never()).delete(any());
            verify(workMapper, never()).delete(any());
            verify(familyMapper, never()).delete(any());
            verify(emergencyMapper, never()).delete(any());
            verify(educationMapper, never()).insert(any(ProfileEducation.class));
        }

        @Test
        @DisplayName("学历非空 → 先删后插并回填 employeeId；其余 null 域不动")
        void replacesEducations() {
            service.replace(100L, List.of(new ProfileEducationDto(null, "清华大学", "计算机", "本科", null, null)),
                    null, null, null);

            verify(educationMapper).delete(any());
            ArgumentCaptor<ProfileEducation> captor = ArgumentCaptor.forClass(ProfileEducation.class);
            verify(educationMapper).insert(captor.capture());
            assertEquals(100L, captor.getValue().getEmployeeId(), "插入行应回填 employeeId");
            assertEquals("清华大学", captor.getValue().getSchool());

            verify(workMapper, never()).delete(any());
            verify(familyMapper, never()).delete(any());
            verify(emergencyMapper, never()).delete(any());
        }

        @Test
        @DisplayName("学历为空列表 → 仅删除不插入（清空该域）")
        void emptyListDeletesOnly() {
            service.replace(100L, List.of(), null, null, null);

            verify(educationMapper).delete(any());
            verify(educationMapper, never()).insert(any(ProfileEducation.class));
        }
    }
}
