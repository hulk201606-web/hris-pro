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

    /** 查询全部法人主体（组织主数据的法定实体层，组织树/雇佣关系均挂在法人主体下）。 */
    public List<CatalogResponse> list() {
        return mapper.selectList(null).stream().map(this::toResponse).toList();
    }

    /** 新建法人主体：USCC 转大写，默认币种 CNY、状态有效。 */
    public CatalogResponse create(CatalogRequest req) {
        OrgLegalEntity e = new OrgLegalEntity();
        e.setName(req.name().trim());
        e.setUscc(req.code() == null ? null : req.code().trim().toUpperCase());
        e.setCurrency("CNY");
        e.setStatus(req.status() == null ? 1 : req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新法人主体：仅名称与启停状态可变更。 */
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
