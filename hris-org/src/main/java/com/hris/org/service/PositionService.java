package com.hris.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.dto.PositionDeptRequest;
import com.hris.org.entity.OrgPosition;
import com.hris.org.entity.OrgPositionDept;
import com.hris.org.mapper.OrgPositionDeptMapper;
import com.hris.org.mapper.OrgPositionMapper;
import com.hris.org.util.CodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 岗位管理（ORG-F-006 + 深化：岗位序列 job_family、岗位×部门多对多挂靠）。
 */
@Service
public class PositionService {

    private final OrgPositionMapper mapper;
    private final OrgPositionDeptMapper positionDeptMapper;

    public PositionService(OrgPositionMapper mapper, OrgPositionDeptMapper positionDeptMapper) {
        this.mapper = mapper;
        this.positionDeptMapper = positionDeptMapper;
    }

    /** 查询全部岗位字典（岗位是组织编制与雇佣关系的载体，与部门多对多通过编制挂钩）。 */
    public List<CatalogResponse> list() {
        return mapper.selectList(null).stream().map(this::toResponse).toList();
    }

    /** 新建岗位：编码缺省按 pos 前缀生成，校验编码唯一。 */
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
        e.setJobFamily(normalizeJobFamily(req.jobFamily()));
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新岗位：名称/启停状态/岗位序列可变更。 */
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
        if (req.jobFamily() != null) {
            e.setJobFamily(normalizeJobFamily(req.jobFamily()));
        }
        mapper.updateById(e);
        return toResponse(e);
    }

    /** 删除岗位（物理删除，删除前校验存在性；挂靠关系一并清除）。 */
    public void delete(Long id) {
        if (mapper.selectById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "岗位不存在");
        }
        positionDeptMapper.delete(new LambdaQueryWrapper<OrgPositionDept>()
                .eq(OrgPositionDept::getPositionId, id));
        mapper.deleteById(id);
    }

    /**
     * 岗位×部门挂靠整体替换（POST /org/positions/{id}/depts）：先删后插，
     * deptIds 去重保序；传空列表即清空挂靠。幂等：重复提交结果一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> replaceDepts(Long positionId, PositionDeptRequest req) {
        requirePosition(positionId);
        List<Long> deptIds = req == null || req.deptIds() == null
                ? List.of() : new LinkedHashSet<>(req.deptIds()).stream().toList();
        positionDeptMapper.delete(new LambdaQueryWrapper<OrgPositionDept>()
                .eq(OrgPositionDept::getPositionId, positionId));
        for (Long deptId : deptIds) {
            OrgPositionDept row = new OrgPositionDept();
            row.setPositionId(positionId);
            row.setDeptId(deptId);
            positionDeptMapper.insert(row);
        }
        return deptIds;
    }

    /** 查询岗位挂靠的部门 ID 列表（按挂靠行 ID 排序）。 */
    public List<Long> listDepts(Long positionId) {
        requirePosition(positionId);
        return positionDeptMapper.selectList(new LambdaQueryWrapper<OrgPositionDept>()
                        .eq(OrgPositionDept::getPositionId, positionId))
                .stream().map(OrgPositionDept::getDeptId).toList();
    }

    /** 清空岗位挂靠（DELETE /org/positions/{id}/depts）。 */
    @Transactional(rollbackFor = Exception.class)
    public void clearDepts(Long positionId) {
        requirePosition(positionId);
        positionDeptMapper.delete(new LambdaQueryWrapper<OrgPositionDept>()
                .eq(OrgPositionDept::getPositionId, positionId));
    }

    private void requirePosition(Long positionId) {
        if (positionId == null || mapper.selectById(positionId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "岗位不存在");
        }
    }

    private String normalizeJobFamily(String jobFamily) {
        return jobFamily == null || jobFamily.isBlank() ? null : jobFamily.trim();
    }

    private void ensureCodeAvailable(String code) {
        Long cnt = mapper.selectCount(new LambdaQueryWrapper<OrgPosition>().eq(OrgPosition::getCode, code));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位编码已存在");
        }
    }

    private CatalogResponse toResponse(OrgPosition e) {
        return new CatalogResponse(e.getId(), e.getName(), e.getCode(), e.getStatus(), null,
                e.getJobFamily());
    }
}
