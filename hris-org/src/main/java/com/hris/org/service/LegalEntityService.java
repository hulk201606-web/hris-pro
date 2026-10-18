package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.entity.OrgLegalEntity;
import com.hris.org.mapper.OrgLegalEntityMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 法人主体管理（ORG-F-001）。
 */
@Service
public class LegalEntityService {

    private final OrgLegalEntityMapper mapper;

    public LegalEntityService(OrgLegalEntityMapper mapper) {
        this.mapper = mapper;
    }

    public List<CatalogResponse> list() {
        return mapper.selectList(null).stream().map(this::toResponse).toList();
    }

    public CatalogResponse create(CatalogRequest req) {
        OrgLegalEntity e = new OrgLegalEntity();
        e.setName(req.name().trim());
        e.setUscc(req.code() == null ? null : req.code().trim().toUpperCase());
        e.setCurrency("CNY");
        e.setStatus(req.status() == null ? 1 : req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    public CatalogResponse update(Long id, CatalogRequest req) {
        OrgLegalEntity e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "法人主体不存在");
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

    private CatalogResponse toResponse(OrgLegalEntity e) {
        return new CatalogResponse(e.getId(), e.getName(), e.getUscc(), e.getStatus(), null);
    }
}
