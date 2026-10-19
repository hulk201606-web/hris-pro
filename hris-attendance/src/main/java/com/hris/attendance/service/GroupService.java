package com.hris.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hris.attendance.dto.GroupDtos.GroupRequest;
import com.hris.attendance.dto.GroupDtos.GroupResponse;
import com.hris.attendance.entity.AttGroup;
import com.hris.attendance.mapper.AttGroupMapper;
import com.hris.attendance.util.CodeUtil;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 考勤班组（ATT-F-001）。班组是考勤政策（工作制/打卡方式/时区）的挂载点。
 */
@Service
public class GroupService {

    private final AttGroupMapper groupMapper;

    public GroupService(AttGroupMapper groupMapper) {
        this.groupMapper = groupMapper;
    }

    /** 班组列表（按编码排序）。 */
    public List<GroupResponse> list() {
        return groupMapper.selectList(new LambdaQueryWrapper<AttGroup>()
                        .orderByAsc(AttGroup::getCode))
                .stream().map(this::toResponse).toList();
    }

    /** 单个班组；不存在抛 10002。 */
    public GroupResponse get(Long id) {
        return toResponse(require(id));
    }
    /** 新建记录，落库后返回响应；@Transactional 保证写入原子性。 */

    @Transactional(rollbackFor = Exception.class)
    public GroupResponse create(GroupRequest req) {
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("ag");
        }
        ensureCodeAvailable(code, null);

        AttGroup group = new AttGroup();
        group.setId(IdWorker.getId());
        group.setName(req.name().trim());
        group.setCode(code);
        group.setLeaderEmpId(req.leaderEmpId());
        groupMapper.insert(group);
        return toResponse(group);
    }
    /** 按主键更新记录，不存在抛 NOT_FOUND；乐观锁防并发覆盖。 */

    @Transactional(rollbackFor = Exception.class)
    public GroupResponse update(Long id, GroupRequest req) {
        AttGroup group = require(id);
        if (req.name() != null && !req.name().isBlank()) {
            group.setName(req.name().trim());
        }
        if (req.code() != null && !req.code().isBlank()) {
            String code = CodeUtil.normalize(req.code());
            ensureCodeAvailable(code, id);
            group.setCode(code);
        }
        if (req.leaderEmpId() != null) {
            group.setLeaderEmpId(req.leaderEmpId());
        }
        if (groupMapper.updateById(group) == 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(group);
    }

    /** 加载班组，不存在抛 10002（供其他 service 校验外键）。 */
    public AttGroup require(Long id) {
        AttGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "考勤班组不存在");
        }
        return group;
    }

    private void ensureCodeAvailable(String code, Long excludeId) {
        LambdaQueryWrapper<AttGroup> qw = new LambdaQueryWrapper<AttGroup>().eq(AttGroup::getCode, code);
        if (excludeId != null) {
            qw.ne(AttGroup::getId, excludeId);
        }
        Long cnt = groupMapper.selectCount(qw);
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.ATT_ERROR, "班组编码已存在");
        }
    }

    private GroupResponse toResponse(AttGroup g) {
        return new GroupResponse(g.getId(), g.getName(), g.getCode(), g.getLeaderEmpId(),
                g.getCreatedAt(), g.getUpdatedAt());
    }
}
