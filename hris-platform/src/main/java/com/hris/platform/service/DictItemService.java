package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;
import com.hris.platform.dto.DictDtos.DictItemRequest;
import com.hris.platform.dto.DictDtos.DictItemResponse;
import com.hris.platform.entity.SysDictItem;
import com.hris.platform.entity.SysDictType;
import com.hris.platform.mapper.DictRefProbeMapper;
import com.hris.platform.mapper.SysDictItemMapper;
import com.hris.platform.mapper.SysDictTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 字典项管理（PLT-F-007，广播表）。
 *
 * <p>引用保护（深化）：删除字典项前按 curated 静态映射（type_code → 业务表.列）
 * 查业务表引用计数，count>0 抛 PARAM_INVALID 拒绝。映射覆盖一期已确证的消费点：
 * 合同类型（emp_contract.type）、证件类型（emp_employee.cert_type）；
 * 请假类型走 att_leave_type 外键（leave_type_id）而非字典值，不在映射内。
 * 其余 type_code 无确证消费点，探测保持恒 false（全仓 grep 结论见交付报告）。</p>
 */
@Service
public class DictItemService {

    private static final Logger log = LoggerFactory.getLogger(DictItemService.class);

    /**
     * curated 引用映射：字典类型编码 → 业务表（可含 schema 前缀）.列。
     * 仅收录经全仓 grep + information_schema 确证以「字典值」落库的列；
     * 表/列名进入 DictRefProbeMapper 的 ${} 拼接前必经本白名单。
     */
    private static final Map<String, String[]> CURATED_REFS = Map.of(
            "contract_type", new String[]{"emp_db.emp_contract", "type"},
            "cert_type", new String[]{"emp_db.emp_employee", "cert_type"},
            "id_type", new String[]{"emp_db.emp_employee", "cert_type"});

    private final SysDictItemMapper itemMapper;
    private final SysDictTypeMapper typeMapper;
    private final DictRefProbeMapper refProbeMapper;

    public DictItemService(SysDictItemMapper itemMapper, SysDictTypeMapper typeMapper,
                           DictRefProbeMapper refProbeMapper) {
        this.itemMapper = itemMapper;
        this.typeMapper = typeMapper;
        this.refProbeMapper = refProbeMapper;
    }

    /** 按字典类型编码查询字典项列表。 */
    public List<DictItemResponse> listByType(String typeCode) {
        String code = normalize(typeCode);
        return itemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getTypeCode, code)
                        .orderByAsc(SysDictItem::getSort)
                        .orderByAsc(SysDictItem::getId))
                .stream().map(this::toResponse).toList();
    }

    /** 在指定字典类型下新建字典项。 */
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

    /** 更新字典项（乐观锁防并发覆盖）。 */
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

    /** 删除字典项（逻辑删除）。 */
    public void delete(Long id) {
        SysDictItem e = require(id);
        assertNotReferenced(e.getTypeCode(), e.getValue());
        itemMapper.deleteById(id);
    }

    private void assertNotReferenced(String typeCode, String value) {
        long count = probeReferenced(typeCode, value);
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字典项已被 " + count + " 条业务数据引用");
        }
    }

    /**
     * 引用探测：type_code 命中 curated 映射时按映射查业务表 count（租户隔离）；
     * 未命中映射的消费点保持恒 0。探测异常降级为 0 并告警（不阻断删除，逻辑删除兜底）。
     */
    private long probeReferenced(String typeCode, String value) {
        String[] ref = CURATED_REFS.get(typeCode);
        if (ref == null) {
            log.debug("dict item reference probe: no curated mapping, type={}, value={}", typeCode, value);
            return 0;
        }
        Long tenantId = TraceContext.getTenantId();
        if (tenantId == null) {
            log.warn("dict item reference probe skipped: missing tenant context, type={}, value={}",
                    typeCode, value);
            return 0;
        }
        try {
            return refProbeMapper.countByValue(ref[0], ref[1], value, tenantId);
        } catch (Exception e) {
            log.warn("dict item reference probe failed (allow delete, logical-delete backstop): "
                    + "type={}, value={}: {}", typeCode, value, e.getMessage());
            return 0;
        }
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
