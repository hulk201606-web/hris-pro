package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.DictDtos.DictTypeRequest;
import com.hris.platform.dto.DictDtos.DictTypeResponse;
import com.hris.platform.entity.SysDictType;
import com.hris.platform.mapper.SysDictTypeMapper;
import com.hris.platform.util.CodeUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典类型管理（PLT-F-007，广播表）。
 */
@Service
public class DictTypeService {

    private final SysDictTypeMapper mapper;

    public DictTypeService(SysDictTypeMapper mapper) {
        this.mapper = mapper;
    }

    /** 查询全部字典类型。 */
    public List<DictTypeResponse> list() {
        return mapper.selectList(new LambdaQueryWrapper<SysDictType>().orderByAsc(SysDictType::getCode))
                .stream().map(this::toResponse).toList();
    }

    /** 新建字典类型。 */
    public DictTypeResponse create(DictTypeRequest req) {
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("dict");
        }
        ensureCodeAvailable(code);
        SysDictType e = new SysDictType();
        e.setName(req.name().trim());
        e.setCode(code);
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新字典类型（乐观锁防并发覆盖）。 */
    public DictTypeResponse update(Long id, DictTypeRequest req) {
        SysDictType e = require(id);
        if (req.name() != null && !req.name().isBlank()) {
            e.setName(req.name().trim());
        }
        mapper.updateById(e);
        return toResponse(e);
    }

    /** 删除字典类型（逻辑删除）。 */
    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    private SysDictType require(Long id) {
        SysDictType e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
        return e;
    }

    private void ensureCodeAvailable(String code) {
        Long cnt = mapper.selectCount(new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getCode, code));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字典类型编码已存在");
        }
    }

    private DictTypeResponse toResponse(SysDictType e) {
        return new DictTypeResponse(e.getId(), e.getName(), e.getCode(), e.getUpdatedAt());
    }
}
