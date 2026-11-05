package com.hris.flow.dto;

import java.time.LocalDateTime;

/**
 * 节点附件与结构化意见标签 DTO（FLW-F-020）。
 */
public final class TaskExtDtos {

    private TaskExtDtos() {
    }

    /**
     * 新增节点附件请求。
     *
     * @param fileId   PLT 文件 ID（数字或对象键均可，统一按字符串落库）
     * @param fileName 文件名（前端展示用快照）
     */
    public record AddAttachmentRequest(String fileId, String fileName) {
    }

    /** 节点附件视图。 */
    public record AttachmentView(Long id, String instanceId, String taskId, String fileId,
                                 String fileName, Long uploadedBy, LocalDateTime createdAt) {
    }

    /**
     * 结构化意见标签请求。
     *
     * @param tag     标签枚举 RECHECK_DATA/NEED_MORE_INFO/COMPLIANT/OTHER
     * @param comment 可选说明
     */
    public record OpinionTagRequest(String tag, String comment) {
    }

    /** 意见标签视图。 */
    public record OpinionTagView(Long id, String taskId, String tag, String comment,
                                 Long operator, LocalDateTime createdAt) {
    }
}
