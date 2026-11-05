package com.hris.rag.service;

import com.hris.rag.dto.ApprovalDtos.ApprovalCallbackResponse;
import com.hris.rag.entity.DocVersion;
import com.hris.rag.mapper.DocVersionMapper;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库域审批回调（flow → kb，docs/19 §9 遗留项四域接入）。
 *
 * <p>幂等状态迁移铁律：先读当前状态，仅待发布状态可迁移，终态/不可迁移状态返回 changed=false，
 * 重复回调不产生任何副作用。</p>
 *
 * <p>状态迁移语义（与 docs/db/schema-mysql.sql 枚举对齐）：
 * <ul>
 *   <li>KBD_PUBLISH → kb_doc_version.status（DRAFT/PENDING/PUBLISHED/EXPIRED/ARCHIVED）：
 *       PENDING 时 approve→PUBLISHED（审批通过正式发布）；PUBLISHED/EXPIRED/ARCHIVED 终态
 *       changed=false；reject 无载体（枚举无 REJECTED，驳回即维持 PENDING 待修订重提）
 *       changed=false。</li>
 * </ul></p>
 *
 * <p>bizKey 约定：rag 域当前无 KBD_PUBLISH 审批 Feign 发起点（接入方后续对齐），约定
 * bizKey = 文档版本主键 id 十进制字符串；若后续发起点改用其它键，须同步修改
 * {@link #handleCallback} 的定位逻辑。</p>
 */
@Service
public class KbApprovalCallbackService {

    private static final Logger log = LoggerFactory.getLogger(KbApprovalCallbackService.class);

    private final DocVersionMapper docVersionMapper;

    public KbApprovalCallbackService(DocVersionMapper docVersionMapper) {
        this.docVersionMapper = docVersionMapper;
    }

    /**
     * 审批结果回调入口（bizType：KBD_PUBLISH）。
     * 未知 bizType 不报错（返回 changed=false），保持审批动作永不失败的契约。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalCallbackResponse handleCallback(String bizType, String bizKey, boolean approved) {
        if (bizType == null || !"KBD_PUBLISH".equals(bizType)) {
            log.warn("kb 域未知审批 bizType，忽略: bizType={}, bizKey={}", bizType, bizKey);
            return new ApprovalCallbackResponse(false, null);
        }
        return handlePublish(bizKey, approved);
    }

    /**
     * 文档版本发布审批：PENDING → approve:PUBLISHED。
     * DRAFT 未提交流程，PUBLISHED/EXPIRED/ARCHIVED 已终态，均 changed=false。
     */
    private ApprovalCallbackResponse handlePublish(String bizKey, boolean approved) {
        DocVersion e = docVersionMapper.selectById(parseBizKey(bizKey));
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档版本不存在");
        }
        if (!"PENDING".equals(e.getStatus())) {
            return new ApprovalCallbackResponse(false, e.getStatus());
        }
        if (!approved) {
            // 驳回无载体：枚举无 REJECTED，维持 PENDING 待修订重提
            return new ApprovalCallbackResponse(false, e.getStatus());
        }
        e.setStatus("PUBLISHED");
        docVersionMapper.updateById(e);
        return new ApprovalCallbackResponse(true, e.getStatus());
    }

    /** bizKey 解析（约定 = 主键 id 十进制字符串），非法格式抛 PARAM_INVALID 保持与 flow 重试契约。 */
    private Long parseBizKey(String bizKey) {
        try {
            return Long.parseLong(bizKey);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizKey 非法（约定为文档版本 id）: " + bizKey);
        }
    }
}
