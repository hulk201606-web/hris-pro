package com.hris.org.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.org.dto.CatalogRequest;
import com.hris.org.dto.CatalogResponse;
import com.hris.org.service.PositionService;
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
 * 岗位端点。
 */
@RestController
@RequestMapping("/api/v1/org")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    /** 查询全部岗位（岗位字典，与部门解耦，挂在部门上通过雇佣关系关联）。 */
    @RequirePerm("org:position:read")
    @GetMapping("/positions")
    public R<List<CatalogResponse>> list() {
        return R.ok(positionService.list());
    }

    /** 新建岗位：编码缺省自动生成（pos 前缀），并校验编码唯一。 */
    @RequirePerm("org:position:write")
    @PostMapping("/positions")
    public R<CatalogResponse> create(@Valid @RequestBody CatalogRequest req) {
        return R.ok(positionService.create(req));
    }

    /** 更新岗位：仅允许改名称与启停状态。 */
    @RequirePerm("org:position:write")
    @PutMapping("/positions/{id}")
    public R<CatalogResponse> update(@PathVariable Long id, @Valid @RequestBody CatalogRequest req) {
        return R.ok(positionService.update(id, req));
    }

    /** 删除岗位（物理删除；已被员工雇佣关系引用的岗位删除前未做强校验，需调用方先确认）。 */
    @RequirePerm("org:position:write")
    @DeleteMapping("/positions/{id}")
    public R<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return R.ok();
    }
}
