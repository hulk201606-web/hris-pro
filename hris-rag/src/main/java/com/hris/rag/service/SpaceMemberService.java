package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.SpaceMemberDtos.SpaceMemberCreateRequest;
import com.hris.rag.dto.SpaceMemberDtos.SpaceMemberResponse;
import com.hris.rag.dto.SpaceMemberDtos.SpaceMemberUpdateRequest;
import com.hris.rag.entity.SpaceMember;
import com.hris.rag.mapper.SpaceMemberMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SpaceMember 服务（CRUD）。
 */
@Service
public class SpaceMemberService {

    private final SpaceMemberMapper mapper;

    public SpaceMemberService(SpaceMemberMapper mapper) {
        this.mapper = mapper;
    }

    public SpaceMemberResponse get(Long id) {
        SpaceMember e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SpaceMember 不存在");
        }
        return toResponse(e);
    }

    public PageResult<SpaceMemberResponse> list(long page, long size) {
        IPage<SpaceMember> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<SpaceMemberResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public SpaceMemberResponse create(SpaceMemberCreateRequest req) {
        SpaceMember e = new SpaceMember();
        e.setSpaceId(req.spaceId());
        e.setUserId(req.userId());
        e.setRole(req.role());
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public SpaceMemberResponse update(Long id, SpaceMemberUpdateRequest req) {
        SpaceMember e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "SpaceMember 不存在");
        }
        e.setSpaceId(req.spaceId());
        e.setUserId(req.userId());
        e.setRole(req.role());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "SpaceMember 不存在");
        }
    }

    private SpaceMemberResponse toResponse(SpaceMember e) {
        return new SpaceMemberResponse(e.getId(), e.getSpaceId(), e.getUserId(), e.getRole(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
