package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.platform.dto.PageResult;
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

    private final ReportService reportService;
    private final ExportTaskService exportTaskService;

    public ReportController(ReportService reportService, ExportTaskService exportTaskService) {
        this.reportService = reportService;
        this.exportTaskService = exportTaskService;
    }

    @GetMapping("/defs")
    public R<List<ReportDefResponse>> listDefs() {
        return R.ok(reportService.list());
    }

    @PostMapping("/defs")
    public R<ReportDefResponse> createDef(@Valid @RequestBody ReportDefRequest req) {
        return R.ok(reportService.create(req));
    }

    @PutMapping("/defs/{id}")
    public R<ReportDefResponse> updateDef(@PathVariable Long id, @Valid @RequestBody ReportDefRequest req) {
        return R.ok(reportService.update(id, req));
    }

    @DeleteMapping("/defs/{id}")
    public R<Void> deleteDef(@PathVariable Long id) {
        reportService.delete(id);
        return R.ok();
    }

    @PostMapping("/exports")
    public R<ExportTaskResponse> createExport(@RequestBody ExportTaskRequest req) {
        return R.ok(exportTaskService.create(req));
    }

    @GetMapping("/exports")
    public R<PageResult<ExportTaskResponse>> listExports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(exportTaskService.query(status, page, size));
    }
}
