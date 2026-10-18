package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.entity.OrgPosition;
import com.hris.org.mapper.OrgPositionMapper;
import com.hris.org.util.CodeUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 岗位管理（ORG-F-006）。
 */
@Service
public class PositionService {

    private final OrgPositionMapper mapper;

    public PositionService(OrgPositionMapper mapper) {
        this.mapper = mapper;
    }

    public List<CatalogResponse> list() {
        return mapper.selectList(null).stream().map(this::toResponse).toList();
    }

    public CatalogResponse create(CatalogRequest req) {
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("pos");
        }
        ensureCodeAvailable(code);
        OrgPosition e = new OrgPosition();
        e.setName(req.name().trim());
        e.setCode(code);
        e.setStatus(req.status() == null ? 1 : req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    public CatalogResponse update(Long id, CatalogRequest req) {
        OrgPosition e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "岗位不存在");
        }
        if (req.name() != null && !req.name().isBlank()) {
            e.setName(req.name().trim());
        }
        if (req.status() != null) {
            e.setStatus(req.status());
        }
        mapper.updateById(e);
        return toResponse(e);
    }

    public void delete(Long id) {
        if (mapper.selectById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "岗位不存在");
        }
        mapper.deleteById(id);
    }

    private void ensureCodeAvailable(String code) {
        Long cnt = mapper.selectCount(new LambdaQueryWrapper<OrgPosition>().eq(OrgPosition::getCode, code));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位编码已存在");
        }
    }

    private CatalogResponse toResponse(OrgPosition e) {
        return new CatalogResponse(e.getId(), e.getName(), e.getCode(), e.getStatus(), null);
    }
}
