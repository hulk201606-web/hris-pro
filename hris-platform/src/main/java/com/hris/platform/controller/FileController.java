package com.hris.platform.controller;

import com.hris.common.api.R;
import com.hris.common.security.RequirePerm;
import com.hris.platform.dto.FileDtos.FileDownloadResponse;
import com.hris.platform.dto.FileDtos.FileMetaResponse;
import com.hris.platform.dto.FileDtos.PresignUploadRequest;
import com.hris.platform.dto.FileDtos.PresignUploadResponse;
import com.hris.platform.service.FileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一文件服务端点（PLT-F-001/002）。
 */
@RestController
@RequestMapping("/api/v1/plt/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @RequirePerm("plt:file:write")
    @PostMapping("/presign-upload")
    public R<PresignUploadResponse> presignUpload(@Valid @RequestBody PresignUploadRequest req) {
        return R.ok(fileService.presignUpload(req));
    }

    @RequirePerm("plt:file:read")
    @GetMapping("/meta/{id}")
    public R<FileMetaResponse> meta(@PathVariable Long id) {
        return R.ok(fileService.getMeta(id));
    }

    @RequirePerm("plt:file:read")
    @GetMapping("/download")
    public R<FileDownloadResponse> download(@RequestParam Long id) {
        return R.ok(fileService.download(id));
    }
}
