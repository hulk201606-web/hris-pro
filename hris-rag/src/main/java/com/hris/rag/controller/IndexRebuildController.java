package com.hris.rag.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.rag.service.IndexRebuildService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识切片索引重建端点（docs/05 §1 手动重建入口）。
 *
 * <p>阶段 A：占位向量验证 chunk → Milvus 读写链路；阶段 B 接入 embedding 后语义化。</p>
 */
@RestController
@RequestMapping("/api/v1/kb")
public class IndexRebuildController {

    private final IndexRebuildService indexRebuildService;

    public IndexRebuildController(IndexRebuildService indexRebuildService) {
        this.indexRebuildService = indexRebuildService;
    }

    @PostMapping("/index/rebuild")
    @RequirePerm("kb:chunk:write")
    public R<Integer> rebuild(@RequestParam Long spaceId) {
        return R.ok(indexRebuildService.rebuild(spaceId));
    }
}
