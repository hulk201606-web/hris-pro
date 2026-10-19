package com.hris.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.recruit.dto.ChannelDtos.ChannelCreateRequest;
import com.hris.recruit.dto.ChannelDtos.ChannelResponse;
import com.hris.recruit.dto.ChannelDtos.ChannelUpdateRequest;
import com.hris.recruit.entity.Channel;
import com.hris.recruit.mapper.ChannelMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 招聘渠道（Channel）：招聘来源渠道字典（内推/猎头/招聘网站等），用于投递归因。
 *
 * <p>业务域：招聘流程（recruit）；分层：Service。</p>
 */
@Service
public class ChannelService {

    private final ChannelMapper mapper;

    public ChannelService(ChannelMapper mapper) {
        this.mapper = mapper;
    }

    /** 按 ID 查询，不存在抛 NOT_FOUND。 */
    public ChannelResponse get(Long id) {
        Channel e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Channel 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询列表。 */
    public PageResult<ChannelResponse> list(long page, long size) {
        IPage<Channel> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<ChannelResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建记录（事务）。 */
    @Transactional(rollbackFor = Exception.class)
    public ChannelResponse create(ChannelCreateRequest req) {
        Channel e = new Channel();
        e.setName(req.name());
        e.setCode(req.code());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新记录（事务）；数据被并发变更时抛 RCS_ERROR。 */
    @Transactional(rollbackFor = Exception.class)
    public ChannelResponse update(Long id, ChannelUpdateRequest req) {
        Channel e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Channel 不存在");
        }
        e.setName(req.name());
        e.setCode(req.code());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RCS_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除记录（事务）；不存在抛 NOT_FOUND。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Channel 不存在");
        }
    }

    private ChannelResponse toResponse(Channel e) {
        return new ChannelResponse(e.getId(), e.getName(), e.getCode(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
