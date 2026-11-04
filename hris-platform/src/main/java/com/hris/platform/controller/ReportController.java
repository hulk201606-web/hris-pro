package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.platform.dto.PageResult;
import com.hris.platform.dto.ReportDtos.CatalogItem;
import com.hris.platform.dto.ReportDtos.ExportTaskRequest;
import com.hris.platform.dto.ReportDtos.ExportTaskResponse;
import com.hris.platform.dto.ReportDtos.ReportDefRequest;
import com.hris.platform.dto.ReportDtos.ReportDefResponse;
import com.hris.platform.service.ExportTaskService;
import com.hris.platform.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报表中心端点（PLT-F-005）：报表定义 CRUD + 异步导出任务。
 */
@RestController
@RequestMapping("/api/v1/plt/reports")
public class ReportController {

    /** 需求约定的五类预置报表主题，先以常量目录暴露，避免各租户重复 seed。 */
    private static final List<CatalogItem> CATALOG = List.of(
            new CatalogItem("HR_STRUCTURE", "人力结构", "在职人数、司龄/年龄/学历分布", List.of("EXCEL", "PDF")),
            new CatalogItem("EMP_CHANGE", "人员异动", "入职/转正/调动/离职周转统计", List.of("EXCEL", "PDF")),
            new CatalogItem("ATTENDANCE", "考勤汇总", "出勤率、加班、假期消耗", List.of("EXCEL")),
            new CatalogItem("RECRUITMENT", "招聘漏斗", "需求→候选人→Offer→录用转化", List.of("EXCEL", "PDF")),
            new CatalogItem("PAYROLL_COST", "薪酬成本", "期间薪酬总额与部门成本分摊", List.of("EXCEL"))
    );

    private final ReportService reportService;
    private final ExportTaskService exportTaskService;

    public ReportController(ReportService reportService, ExportTaskService exportTaskService) {
        this.reportService = reportService;
        this.exportTaskService = exportTaskService;
    }

    /** 预置报表目录（PLT-F-005）。业务明细取数依赖 PLT-F-006 只读聚合库。 */
    @RequirePerm("plt:report:read")
    @GetMapping("/catalog")
    public R<List<CatalogItem>> catalog() {
        return R.ok(CATALOG);
    }

    @RequirePerm("plt:report:read")
    @GetMapping("/defs")
    public R<List<ReportDefResponse>> listDefs() {
        return R.ok(reportService.list());
    }

    @RequirePerm("plt:report:write")
    @PostMapping("/defs")
    public R<ReportDefResponse> createDef(@Valid @RequestBody ReportDefRequest req) {
        return R.ok(reportService.create(req));
    }

    @RequirePerm("plt:report:write")
    @PutMapping("/defs/{id}")
    public R<ReportDefResponse> updateDef(@PathVariable Long id, @Valid @RequestBody ReportDefRequest req) {
        return R.ok(reportService.update(id, req));
    }

    @RequirePerm("plt:report:write")
    @DeleteMapping("/defs/{id}")
    public R<Void> deleteDef(@PathVariable Long id) {
        reportService.delete(id);
        return R.ok();
    }

    @RequirePerm("plt:report:export")
    @PostMapping("/exports")
    public R<ExportTaskResponse> createExport(@RequestBody ExportTaskRequest req) {
        return R.ok(exportTaskService.create(req));
    }

    @RequirePerm("plt:report:read")
    @GetMapping("/exports")
    public R<PageResult<ExportTaskResponse>> listExports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(exportTaskService.query(status, page, size));
    }
}
