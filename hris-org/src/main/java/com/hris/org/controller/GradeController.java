package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.service.GradeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 职级端点。
 */
@RestController
@RequestMapping("/api/v1/org")
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    /** 查询全部职级（职级字典，如 P5/M2，归属某个职级带）。 */
    @GetMapping("/grades")
    public R<List<CatalogResponse>> list() {
        return R.ok(gradeService.list());
    }

    /** 新建职级：编码缺省自动生成（grade 前缀），校验编码唯一，可挂载到职级带 gradeBandId。 */
    @PostMapping("/grades")
    public R<CatalogResponse> create(@Valid @RequestBody CatalogRequest req) {
        return R.ok(gradeService.create(req));
    }

    /** 更新职级：允许改名称、所属职级带与启停状态。 */
    @PutMapping("/grades/{id}")
    public R<CatalogResponse> update(@PathVariable Long id, @Valid @RequestBody CatalogRequest req) {
        return R.ok(gradeService.update(id, req));
    }

    /** 删除职级（物理删除）。 */
    @DeleteMapping("/grades/{id}")
    public R<Void> delete(@PathVariable Long id) {
        gradeService.delete(id);
        return R.ok();
    }
}
