package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.entity.OrgGradeBand;
import com.hris.org.mapper.OrgGradeBandMapper;
import com.hris.org.util.CodeUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 职级带管理（ORG-F-008）。
 */
@Service
public class GradeBandService {

    private final OrgGradeBandMapper mapper;

    public GradeBandService(OrgGradeBandMapper mapper) {
        this.mapper = mapper;
    }

    public List<CatalogResponse> list() {
        return mapper.selectList(null).stream().map(this::toResponse).toList();
    }

    public CatalogResponse create(CatalogRequest req) {
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("band");
        }
        ensureCodeAvailable(code);
        OrgGradeBand e = new OrgGradeBand();
        e.setName(req.name().trim());
        e.setCode(code);
        // minGrade/maxGrade 带宽区间在后续增量经专用字段维护，本增量不通过通用请求设置
        mapper.insert(e);
        return toResponse(e);
    }

    public CatalogResponse update(Long id, CatalogRequest req) {
        OrgGradeBand e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "职级带不存在");
        }
        if (req.name() != null && !req.name().isBlank()) {
            e.setName(req.name().trim());
        }
        mapper.updateById(e);
        return toResponse(e);
    }

    public void delete(Long id) {
        if (mapper.selectById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "职级带不存在");
        }
        mapper.deleteById(id);
    }

    private void ensureCodeAvailable(String code) {
        Long cnt = mapper.selectCount(new LambdaQueryWrapper<OrgGradeBand>().eq(OrgGradeBand::getCode, code));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "职级带编码已存在");
        }
    }

    private CatalogResponse toResponse(OrgGradeBand e) {
        return new CatalogResponse(e.getId(), e.getName(), e.getCode(), null, null);
    }
}
