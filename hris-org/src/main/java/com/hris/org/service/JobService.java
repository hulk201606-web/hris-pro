package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.entity.OrgJob;
import com.hris.org.mapper.OrgJobMapper;
import com.hris.org.util.CodeUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 职务管理（ORG-F-007）。
 */
@Service
public class JobService {

    private final OrgJobMapper mapper;

    public JobService(OrgJobMapper mapper) {
        this.mapper = mapper;
    }

    /** 查询全部职务字典。 */
    public List<CatalogResponse> list() {
        return mapper.selectList(null).stream().map(this::toResponse).toList();
    }

    /** 新建职务：编码缺省按 job 前缀生成，落库前校验编码唯一。 */
    public CatalogResponse create(CatalogRequest req) {
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("job");
        }
        ensureCodeAvailable(code);
        OrgJob e = new OrgJob();
        e.setName(req.name().trim());
        e.setCode(code);
        e.setStatus(req.status() == null ? 1 : req.status());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新职务：仅名称与启停状态可变更，编码作为身份标识不可改。 */
    public CatalogResponse update(Long id, CatalogRequest req) {
        OrgJob e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "职务不存在");
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

    /** 删除职务（物理删除，删除前校验存在性）。 */
    public void delete(Long id) {
        if (mapper.selectById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "职务不存在");
        }
        mapper.deleteById(id);
    }

    private void ensureCodeAvailable(String code) {
        Long cnt = mapper.selectCount(new LambdaQueryWrapper<OrgJob>().eq(OrgJob::getCode, code));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "职务编码已存在");
        }
    }

    private CatalogResponse toResponse(OrgJob e) {
        return new CatalogResponse(e.getId(), e.getName(), e.getCode(), e.getStatus(), null);
    }
}
