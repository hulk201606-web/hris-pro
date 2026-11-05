package com.hris.rag.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.rag.dto.FileTextDtos.ExtractTextByFileIdRequest;
import com.hris.rag.dto.FileTextDtos.ExtractTextResponse;
import com.hris.rag.service.FileTextService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件文本层抽取端点（批 12 #28，简历文件 OCR→文本抽取）。
 *
 * <p><b>契约</b></p>
 * <ul>
 *   <li>{@code POST /api/v1/kb/files/extract-text}（multipart/form-data，part 名 {@code file}，
 *       权限 {@code kb:document:read}）：直传抽取，返回
 *       {@code {text, pages, hasTextLayer, warning}}；</li>
 *   <li>{@code POST /internal/kb/files/extract-text}（application/json，{@code {fileId, fileName?}}，
 *       无权限码，网关封禁外部访问）：recruit 简历解析用，rag 以调用者身份经 platform 文件服务
 *       取件后抽取。</li>
 * </ul>
 *
 * <p>扫描件（无文本层）不是错误：{@code hasTextLayer=false} + {@code warning} 提示需 OCR 引擎，
 * 由调用方决定是否阻断。</p>
 */
@RestController
public class FileTextController {

    private final FileTextService service;

    public FileTextController(FileTextService service) {
        this.service = service;
    }

    /** 直传抽取（multipart 最简路径）。 */
    @RequirePerm("kb:document:read")
    @PostMapping(value = "/api/v1/kb/files/extract-text", consumes = "multipart/form-data")
    public R<ExtractTextResponse> extract(@RequestPart("file") MultipartFile file) {
        return R.ok(service.extract(file));
    }

    /** 服务间按 fileId 抽取（recruit → rag，内部端点）。 */
    @PostMapping("/internal/kb/files/extract-text")
    public R<ExtractTextResponse> extractByFileId(@Valid @RequestBody ExtractTextByFileIdRequest req) {
        return R.ok(service.extractByFileId(req.fileId(), req.fileName()));
    }
}
