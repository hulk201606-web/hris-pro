package com.hris.attendance.service;

import com.hris.attendance.entity.AttGroup;
import com.hris.attendance.entity.AttGroupMember;
import com.hris.attendance.mapper.AttGroupMapper;
import com.hris.attendance.mapper.AttGroupMemberMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 考勤组成员（批6#10）单元测试：全量覆盖语义、单个移除守卫、
 * 节假日年度判定从租户并集收敛为组成员并集（组无成员降级租户并集保持兼容）。
 */
@ExtendWith(MockitoExtension.class)
class GroupMemberServiceTest {

    private static final Long GROUP_ID = 300001L;
    private static final Long EMP_A = 200001L;
    private static final Long EMP_B = 200002L;

    @Mock
    private AttGroupMemberMapper memberMapper;
    @Mock
    private AttGroupMapper groupMapper;
    @Mock
    private GroupService groupService;

    private GroupMemberService service;
    private GroupService realGroupService;

    @BeforeEach
    void setUp() {
        service = new GroupMemberService(memberMapper, groupService);
        realGroupService = new GroupService(groupMapper, memberMapper);
    }

    private static AttGroupMember member(Long groupId, Long empId) {
        AttGroupMember m = new AttGroupMember();
        m.setGroupId(groupId);
        m.setEmpId(empId);
        return m;
    }

    private static AttGroup group(Long id, Integer year) {
        AttGroup g = new AttGroup();
        g.setId(id);
        g.setHolidayCalendarYear(year);
        return g;
    }

    @Nested
    @DisplayName("组成员维护")
    class MemberCrud {

        @Test
        @DisplayName("批量设置（全量覆盖）：先物理删旧，再按去重后的 empIds 重建")
        void setMembersOverwrites() {
            List<Long> result = service.setMembers(GROUP_ID, List.of(EMP_A, EMP_B, EMP_A));

            assertEquals(List.of(EMP_A, EMP_B), result);
            verify(memberMapper).physicalDeleteByGroup(GROUP_ID);
            ArgumentCaptor<AttGroupMember> captor = ArgumentCaptor.forClass(AttGroupMember.class);
            verify(memberMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
            assertEquals(List.of(EMP_A, EMP_B),
                    captor.getAllValues().stream().map(AttGroupMember::getEmpId).toList());
        }

        @Test
        @DisplayName("empIds 为空 → 清空组成员（合法操作，判定降级租户并集）")
        void setMembersEmptyClears() {
            service.setMembers(GROUP_ID, List.of());

            verify(memberMapper).physicalDeleteByGroup(GROUP_ID);
            verify(memberMapper, never()).insert(any(AttGroupMember.class));
        }

        @Test
        @DisplayName("移除不存在的成员 → NOT_FOUND")
        void removeMissingMemberThrows() {
            when(memberMapper.physicalDeleteOne(GROUP_ID, EMP_A)).thenReturn(0);

            BizException e = assertThrows(BizException.class,
                    () -> service.removeMember(GROUP_ID, EMP_A));

            assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("成员列表按 mapper 返回顺序映射（SQL 侧 orderByAsc(emp_id) 保证升序）")
        void listMembers() {
            when(memberMapper.selectList(any())).thenReturn(
                    List.of(member(GROUP_ID, EMP_B), member(GROUP_ID, EMP_A)));

            assertEquals(List.of(EMP_B, EMP_A), service.listMembers(GROUP_ID));
        }
    }

    @Nested
    @DisplayName("节假日年度判定收敛（组成员并集）")
    class CalendarYearConvergence {

        @Test
        @DisplayName("员工所在组绑定年度 → 返回组成员并集，不查租户并集")
        void memberUnionWins() {
            when(memberMapper.selectList(any())).thenReturn(List.of(member(GROUP_ID, EMP_A)));
            when(groupMapper.selectList(any())).thenReturn(List.of(group(GROUP_ID, 2026)));

            List<Integer> years = realGroupService.boundCalendarYearsForEmp(EMP_A);

            assertEquals(List.of(2026), years);
        }

        @Test
        @DisplayName("员工未加入任何组 → 降级租户并集（兼容既有行为）")
        void noMembershipFallsBackToTenantUnion() {
            when(memberMapper.selectList(any())).thenReturn(List.of());
            when(groupMapper.selectList(any())).thenReturn(List.of(group(1L, 2025)));

            List<Integer> years = realGroupService.boundCalendarYearsForEmp(EMP_A);

            assertEquals(List.of(2025), years);
        }

        @Test
        @DisplayName("所在组均未绑定年度 → 降级租户并集")
        void unboundGroupsFallBackToTenantUnion() {
            when(memberMapper.selectList(any())).thenReturn(List.of(member(GROUP_ID, EMP_A)));
            // 第一次调用：所在组年度查询（空）；第二次调用：降级的租户并集查询
            when(groupMapper.selectList(any()))
                    .thenReturn(List.of())
                    .thenReturn(List.of(group(1L, 2027)));

            List<Integer> years = realGroupService.boundCalendarYearsForEmp(EMP_A);

            assertEquals(List.of(2027), years);
        }
    }
}
