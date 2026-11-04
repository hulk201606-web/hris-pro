package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.platform.dto.DictDtos.DictItemRequest;
import com.hris.platform.dto.DictDtos.DictItemResponse;
import com.hris.platform.dto.DictDtos.DictTypeRequest;
import com.hris.platform.dto.DictDtos.DictTypeResponse;
import com.hris.platform.service.DictItemService;
import com.hris.platform.service.DictTypeService;
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
 * 数据字典端点（PLT-F-007）：类型 CRUD + 字典项 CRUD。
 */
@RestController
@RequestMapping("/api/v1/plt/dicts")
public class DictController {

    private final DictTypeService typeService;
    private final DictItemService itemService;

    public DictController(DictTypeService typeService, DictItemService itemService) {
        this.typeService = typeService;
        this.itemService = itemService;
    }

    @RequirePerm("plt:dict:read")
    @GetMapping("/types")
    public R<List<DictTypeResponse>> listTypes() {
        return R.ok(typeService.list());
    }

    @RequirePerm("plt:dict:write")
    @PostMapping("/types")
    public R<DictTypeResponse> createType(@Valid @RequestBody DictTypeRequest req) {
        return R.ok(typeService.create(req));
    }

    @RequirePerm("plt:dict:write")
    @PutMapping("/types/{id}")
    public R<DictTypeResponse> updateType(@PathVariable Long id, @Valid @RequestBody DictTypeRequest req) {
        return R.ok(typeService.update(id, req));
    }

    @RequirePerm("plt:dict:write")
    @DeleteMapping("/types/{id}")
    public R<Void> deleteType(@PathVariable Long id) {
        typeService.delete(id);
        return R.ok();
    }

    @RequirePerm("plt:dict:read")
    @GetMapping("/types/{typeCode}/items")
    public R<List<DictItemResponse>> listItems(@PathVariable String typeCode) {
        return R.ok(itemService.listByType(typeCode));
    }

    @RequirePerm("plt:dict:write")
    @PostMapping("/types/{typeCode}/items")
    public R<DictItemResponse> createItem(@PathVariable String typeCode,
                                          @Valid @RequestBody DictItemRequest req) {
        return R.ok(itemService.create(typeCode, req));
    }

    @RequirePerm("plt:dict:write")
    @PutMapping("/items/{id}")
    public R<DictItemResponse> updateItem(@PathVariable Long id, @Valid @RequestBody DictItemRequest req) {
        return R.ok(itemService.update(id, req));
    }

    @RequirePerm("plt:dict:write")
    @DeleteMapping("/items/{id}")
    public R<Void> deleteItem(@PathVariable Long id) {
        itemService.delete(id);
        return R.ok();
    }
}
