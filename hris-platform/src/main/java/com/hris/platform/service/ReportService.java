package com.hris.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.platform.dto.ReportDtos.ReportDefRequest;
import com.hris.platform.dto.ReportDtos.ReportDefResponse;
import com.hris.platform.entity.SysReportDef;
import com.hris.platform.mapper.SysReportDefMapper;
import com.hris.platform.util.CodeUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报表定义管理（PLT-F-005：预置报表目录，def_json 存查询定义）。
 */
@Service
public class ReportService {

    private final SysReportDefMapper mapper;

    public ReportService(SysReportDefMapper mapper) {
        this.mapper = mapper;
    }

    public List<ReportDefResponse> list() {
        return mapper.selectList(new LambdaQueryWrapper<SysReportDef>().orderByAsc(SysReportDef::getCode))
                .stream().map(this::toResponse).toList();
    }

    public ReportDefResponse create(ReportDefRequest req) {
        String code = CodeUtil.normalize(req.code());
        if (code == null || code.isBlank()) {
            code = CodeUtil.next("rpt");
        }
        ensureCodeAvailable(code);
        SysReportDef e = new SysReportDef();
        e.setName(req.name().trim());
        e.setCode(code);
        e.setDefJson(req.defJson());
        mapper.insert(e);
        return toResponse(e);
    }

    public ReportDefResponse update(Long id, ReportDefRequest req) {
        SysReportDef e = require(id);
        if (req.name() != null && !req.name().isBlank()) {
            e.setName(req.name().trim());
        }
        if (req.defJson() != null) {
            e.setDefJson(req.defJson());
        }
        mapper.updateById(e);
        return toResponse(e);
    }

    public void delete(Long id) {
        require(id);
        mapper.deleteById(id);
    }

    private void ensureCodeAvailable(String code) {
        Long cnt = mapper.selectCount(new LambdaQueryWrapper<SysReportDef>().eq(SysReportDef::getCode, code));
        if (cnt != null && cnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "报表编码已存在");
        }
    }

    private SysReportDef require(Long id) {
        SysReportDef e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "报表定义不存在");
        }
        return e;
    }

    private ReportDefResponse toResponse(SysReportDef e) {
        return new ReportDefResponse(e.getId(), e.getName(), e.getCode(), e.getDefJson(), e.getUpdatedAt());
    }
}
