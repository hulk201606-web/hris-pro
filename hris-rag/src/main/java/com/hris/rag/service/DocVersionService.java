package com.hris.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.rag.dto.DocVersionDtos.DocVersionCreateRequest;
import com.hris.rag.dto.DocVersionDtos.DocVersionResponse;
import com.hris.rag.dto.DocVersionDtos.DocVersionUpdateRequest;
import com.hris.rag.entity.DocVersion;
import com.hris.rag.entity.Document;
import com.hris.rag.mapper.DocVersionMapper;
import com.hris.rag.mapper.DocumentMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DocVersion 服务（docs/19 §9 遗留项：KBD_PUBLISH 审批发起接入）。
 *
 * <p>状态机：DRAFT →（submit 提交发布）PENDING →（审批回调 approve）PUBLISHED；
 * EXPIRED/ARCHIVED 为终态。status 不接受任何请求直填——创建恒置 DRAFT，
 * 后续只走 {@link #submit} / 审批回调 / 既有归档动作。</p>
 *
 * <p>bizKey 约定（与 {@link KbApprovalCallbackService} 对齐）：文档版本主键 id 十进制字符串。
 * fail-open：flow 不可用时版本保留 PENDING、不阻断提交，最终状态由回调驱动。</p>
 */
@Service
public class DocVersionService {

    /** 状态：草稿 */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态：待审 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：已发布（终态） */
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 状态：已失效（终态） */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** 状态：已归档（终态） */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 流程业务类型（模板 key = bizType） */
    public static final String BIZ_TYPE_PUBLISH = "KBD_PUBLISH";

    /** 文档名进入流程 payload 的摘要截断长度（非敏感摘要，docs/04 §6） */
    private static final int TITLE_SUMMARY_LEN = 100;

    private final DocVersionMapper mapper;
    private final DocumentMapper documentMapper;
    private final FlowIntegration flowIntegration;

    public DocVersionService(DocVersionMapper mapper, DocumentMapper documentMapper, FlowIntegration flowIntegration) {
        this.mapper = mapper;
        this.documentMapper = documentMapper;
        this.flowIntegration = flowIntegration;
    }

    /** 按主键查询文档版本详情，不存在则抛 NOT_FOUND。 */
    public DocVersionResponse get(Long id) {
        DocVersion e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "DocVersion 不存在");
        }
        return toResponse(e);
    }

    /** 分页查询文档版本列表。 */
    public PageResult<DocVersionResponse> list(long page, long size) {
        IPage<DocVersion> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<DocVersionResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    /** 新建文档版本：恒以 DRAFT 起步（状态不可由请求直填）。 */
    @Transactional(rollbackFor = Exception.class)
    public DocVersionResponse create(DocVersionCreateRequest req) {
        DocVersion e = new DocVersion();
        e.setDocumentId(req.documentId());
        e.setStatus(STATUS_DRAFT);
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setMilvusCollection(req.milvusCollection());
        mapper.insert(e);
        return toResponse(e);
    }

    /** 更新文档版本（乐观锁防并发覆盖；status 旁路已封死，不接受直填）。 */
    @Transactional(rollbackFor = Exception.class)
    public DocVersionResponse update(Long id, DocVersionUpdateRequest req) {
        DocVersion e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "DocVersion 不存在");
        }
        e.setDocumentId(req.documentId());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setMilvusCollection(req.milvusCollection());
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /**
     * 提交发布：仅 DRAFT → PENDING + 发起 KBD_PUBLISH 流程（bizKey = 版本 id 十进制串）。
     * PENDING 重复提交拒绝；PUBLISHED/EXPIRED/ARCHIVED 终态拒绝。
     * fail-open：flow 不可用时版本保留 PENDING、不阻断提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocVersionResponse submit(Long id) {
        DocVersion e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "DocVersion 不存在");
        }
        if (STATUS_PENDING.equals(e.getStatus())) {
            throw new BizException(ErrorCode.RAG_ERROR, "版本已提交待审，请勿重复提交");
        }
        if (!STATUS_DRAFT.equals(e.getStatus())) {
            throw new BizException(ErrorCode.RAG_ERROR, "当前状态（" + e.getStatus() + "）不可提交发布，仅草稿可提交");
        }
        e.setStatus(STATUS_PENDING);
        String bizKey = String.valueOf(e.getId());
        flowIntegration.start(BIZ_TYPE_PUBLISH, bizKey, payload(e));
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    /** 删除文档版本（逻辑删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "DocVersion 不存在");
        }
    }

    /** 流程 payload：文档名/空间摘要（非敏感，flow 不落库仅作路由）。 */
    private Map<String, Object> payload(DocVersion e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("docVersionId", e.getId());
        payload.put("documentId", e.getDocumentId());
        Document doc = documentMapper.selectById(e.getDocumentId());
        if (doc != null) {
            payload.put("docTitleSummary", summarize(doc.getTitle()));
            payload.put("spaceId", doc.getSpaceId());
        }
        return payload;
    }

    private String summarize(String title) {
        if (title == null || title.length() <= TITLE_SUMMARY_LEN) {
            return title;
        }
        return title.substring(0, TITLE_SUMMARY_LEN) + "...";
    }

    private DocVersionResponse toResponse(DocVersion e) {
        return new DocVersionResponse(e.getId(), e.getDocumentId(), e.getStatus(), e.getEffectiveFrom(), e.getEffectiveTo(), e.getMilvusCollection(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
