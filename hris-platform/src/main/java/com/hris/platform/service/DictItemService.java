package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.DictDtos.DictItemRequest;
import com.hris.platform.dto.DictDtos.DictItemResponse;
import com.hris.platform.entity.SysDictItem;
import com.hris.platform.entity.SysDictType;
import com.hris.platform.mapper.SysDictItemMapper;
import com.hris.platform.mapper.SysDictTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典项管理（PLT-F-007，广播表）。
 *
 * <p>引用保护：字典项被业务数据引用时不可物理删除，统一走逻辑删除（BaseEntity @TableLogic）。
 * 跨服务引用校验依赖业务域提供「按字典值计数」只读接口，platform 本增量不引入 Feign（横向被调用方），
 * 故引用探测留空扩展点，逻辑删除机制已保证数据永不物理清除。</p>
 */
@Service
public class DictItemService {

    private static final Logger log = LoggerFactory.getLogger(DictItemService.class);

    private final SysDictItemMapper itemMapper;
    private final SysDictTypeMapper typeMapper;

    public DictItemService(SysDictItemMapper itemMapper, SysDictTypeMapper typeMapper) {
        this.itemMapper = itemMapper;
        this.typeMapper = typeMapper;
    }

    public List<DictItemResponse> listByType(String typeCode) {
        String code = normalize(typeCode);
        return itemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getTypeCode, code)
                        .orderByAsc(SysDictItem::getSort)
                        .orderByAsc(SysDictItem::getId))
                .stream().map(this::toResponse).toList();
    }

    public DictItemResponse create(String typeCode, DictItemRequest req) {
        String code = normalize(typeCode);
        requireTypeExists(code);
        String value = normalize(req.value());
        if (value == null || value.isBlank()) {
            value = normalize(req.label());
        }
        ensureValueAvailable(code, value);
        SysDictItem e = new SysDictItem();
        e.setTypeCode(code);
        e.setLabel(req.label().trim());
        e.setValue(value);
        e.setSort(req.sort() == null ? 0 : req.sort());
        itemMapper.insert(e);
        return toResponse(e);
    }

    public DictItemResponse update(Long id, DictItemRequest req) {
        SysDictItem e = require(id);
        if (req.label() != null && !req.label().isBlank()) {
            e.setLabel(req.label().trim());
        }
        if (req.sort() != null) {
            e.setSort(req.sort());
        }
        itemMapper.updateById(e);
        return toResponse(e);
    }

    public void delete(Long id) {
        SysDictItem e = require(id);
        assertNotReferenced(e.getTypeCode(), e.getValue());
        itemMapper.deleteById(id);
    }

    private void assertNotReferenced(String typeCode, String value) {
        // 跨服务引用计数接口未接入（platform 无 Feign），MVP 探测恒 false；接入后此处改为调只读计数接口。
        boolean referenced = probeReferenced(typeCode, value);
        if (referenced) {
            throw new BizException(56001, "字典项已被业务数据引用，不可删除");
        }
    }

    /** 引用探测扩展点：返回业务域引用计数 > 0 时为 true。MVP 恒 false。 */
    private boolean probeReferenced(String typeCode, String value) {
        log.debug("dict item reference probe skipped (no cross-service counter): type={}, value={}", typeCode, value);
        return false;
    }

    private void requireTypeExists(String typeCode) {
        Long cnt = typeMapper.selectCount(new LambdaQueryWrapper<SysDictType>().eq(SysDictType::getCode, typeCode));
        if (cnt == null || cnt == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
    }

    private void ensureValueAvailable(String typeCode, String value) {
        Long cnt = itemMapper.selectCount(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getValue, value));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字典项值已存在");
        }
    }

    private SysDictItem require(Long id) {
        SysDictItem e = itemMapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典项不存在");
        }
        return e;
    }

    private String normalize(String s) {
        return s == null ? null : s.trim();
    }

    private DictItemResponse toResponse(SysDictItem e) {
        return new DictItemResponse(e.getId(), e.getTypeCode(), e.getLabel(), e.getValue(), e.getSort());
    }
}
