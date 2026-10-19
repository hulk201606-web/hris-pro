package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.GovernTicketDtos.GovernTicketCreateRequest;
import com.hris.rag.dto.GovernTicketDtos.GovernTicketResponse;
import com.hris.rag.dto.GovernTicketDtos.GovernTicketUpdateRequest;
import com.hris.rag.entity.GovernTicket;
import com.hris.rag.mapper.GovernTicketMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * GovernTicket 服务（CRUD）。
 */
@Service
public class GovernTicketService {

    private final GovernTicketMapper mapper;

    public GovernTicketService(GovernTicketMapper mapper) {
        this.mapper = mapper;
    }

    /** 按主键查询治理工单详情，不存在则抛 NOT_FOUND。 */
    public GovernTicketResponse get(Long id) {
        GovernTicket e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "GovernTicket 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询治理工单列表。 */
    public PageResult<GovernTicketResponse> list(long page, long size) {
        IPage<GovernTicket> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<GovernTicketResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建治理工单。 */
    @Transactional(rollbackFor = Exception.class)
    public GovernTicketResponse create(GovernTicketCreateRequest req) {
        GovernTicket e = new GovernTicket();
        e.setDocVersionId(req.docVersionId());
        e.setSource(req.source());
        e.setStatus(req.status());
        e.setDescription(req.description());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新治理工单（乐观锁防并发覆盖）。 */
    @Transactional(rollbackFor = Exception.class)
    public GovernTicketResponse update(Long id, GovernTicketUpdateRequest req) {
        GovernTicket e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "GovernTicket 不存在");
        }
        e.setDocVersionId(req.docVersionId());
        e.setSource(req.source());
        e.setStatus(req.status());
        e.setDescription(req.description());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除治理工单（逻辑删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "GovernTicket 不存在");
        }
    }

    private GovernTicketResponse toResponse(GovernTicket e) {
        return new GovernTicketResponse(e.getId(), e.getDocVersionId(), e.getSource(), e.getStatus(), e.getDescription(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
