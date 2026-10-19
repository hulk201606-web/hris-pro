package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.entity.OrgGrade;
import com.hris.org.mapper.OrgGradeMapper;
import com.hris.org.util.CodeUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 职级管理（ORG-F-008）。
 */
@Service
public class GradeService {

    private final OrgGradeMapper mapper;

    public GradeService(OrgGradeMapper mapper) {
        this.mapper = mapper;
    }

    /** 查询全部职级字典（含所属职级带 ID）。 */
    public List<CatalogResponse> list() {
        return mapper.selectList(null).stream().map(this::toResponse).toList();
    }

    /** 新建职级：编码缺省按 grade 前缀生成，校验编码唯一，可挂职级带。 */
    public CatalogResponse create(CatalogRequest req) {
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("grade");
        }
        ensureCodeAvailable(code);
        OrgGrade e = new OrgGrade();
        e.setName(req.name().trim());
        e.setCode(code);
        e.setGradeBandId(req.gradeBandId());
        e.setStatus(req.status() == null ? 1 : req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新职级：名称、所属职级带、启停状态可变更。 */
    public CatalogResponse update(Long id, CatalogRequest req) {
        OrgGrade e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "职级不存在");
        }
        if (req.name() != null && !req.name().isBlank()) {
            e.setName(req.name().trim());
        }
        if (req.gradeBandId() != null) {
            e.setGradeBandId(req.gradeBandId());
        }
        if (req.status() != null) {
            e.setStatus(req.status());
        }
        mapper.updateById(e);
        return toResponse(e);
    }

    /** 删除职级（物理删除，删除前校验存在性）。 */
    public void delete(Long id) {
        if (mapper.selectById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "职级不存在");
        }
        mapper.deleteById(id);
    }

    private void ensureCodeAvailable(String code) {
        Long cnt = mapper.selectCount(new LambdaQueryWrapper<OrgGrade>().eq(OrgGrade::getCode, code));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "职级编码已存在");
        }
    }

    private CatalogResponse toResponse(OrgGrade e) {
        return new CatalogResponse(e.getId(), e.getName(), e.getCode(), e.getStatus(), e.getGradeBandId());
    }
}
