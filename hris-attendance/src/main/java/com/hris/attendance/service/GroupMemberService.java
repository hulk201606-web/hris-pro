package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.entity.AttGroupMember;
import com.hris.attendance.mapper.AttGroupMemberMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 考勤组成员维护（批6#10）。
 *
 * <p>批量设置为全量覆盖语义：以请求 empIds 为该组最终成员，先物理删旧再重建；
 * 单个移除走物理删除（唯一键不含 deleted，逻辑删会占位，见 AttGroupMemberMapper）。</p>
 */
@Service
public class GroupMemberService {

    private final AttGroupMemberMapper memberMapper;
    private final GroupService groupService;

    public GroupMemberService(AttGroupMemberMapper memberMapper, GroupService groupService) {
        this.memberMapper = memberMapper;
        this.groupService = groupService;
    }

    /**
     * 批量设置组成员（全量覆盖）：empIds 去重后即为该组最终成员，返回最终成员列表。
     * empIds 为空/缺失表示清空该组成员（合法操作，节假日判定随之降级到租户并集）。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> setMembers(Long groupId, List<Long> empIds) {
        groupService.require(groupId);
        List<Long> distinct = empIds == null ? List.of()
                : empIds.stream().filter(Objects::nonNull).distinct().toList();
        memberMapper.physicalDeleteByGroup(groupId);
        for (Long empId : distinct) {
            AttGroupMember member = new AttGroupMember();
            member.setId(IdWorker.getId());
            member.setGroupId(groupId);
            member.setEmpId(empId);
            memberMapper.insert(member);
        }
        return distinct;
    }

    /** 组成员列表（按员工 ID 升序）。 */
    public List<Long> listMembers(Long groupId) {
        groupService.require(groupId);
        return memberMapper.selectList(new LambdaQueryWrapper<AttGroupMember>()
                        .eq(AttGroupMember::getGroupId, groupId)
                        .orderByAsc(AttGroupMember::getEmpId))
                .stream().map(AttGroupMember::getEmpId).toList();
    }

    /** 移除单个成员；成员不存在抛 10002。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long groupId, Long empId) {
        groupService.require(groupId);
        if (memberMapper.physicalDeleteOne(groupId, empId) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "该员工不在考勤组中");
        }
    }
}
