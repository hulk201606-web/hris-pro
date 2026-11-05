package com.hris.rag.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 文件文本层抽取 DTO（批 12 #28，简历文件 OCR→文本抽取）。
 */
public final class FileTextDtos {

    private FileTextDtos() {
    }

    /**
     * 抽取结果。
     *
     * @param text         文本层内容（无文本层时为空串）
     * @param pages        页数（PDF 取 xmp/meta 页数，未知为 null）
     * @param hasTextLayer 是否含文本层（false 且 warning 提示需 OCR）
     * @param warning      降级说明（成功且无风险时为 null）
     */
    public record ExtractTextResponse(String text, Integer pages, boolean hasTextLayer, String warning) {
    }

    /**
     * 按平台文件 ID 抽取（服务间调用：recruit 只持有 rcu_resume.file_id，无文件字节）。
     * 由 rag 经 platform 文件服务下载后抽取。
     */
    public record ExtractTextByFileIdRequest(
            @NotNull(message = "fileId 不能为空") Long fileId,
            String fileName) {
    }
}
