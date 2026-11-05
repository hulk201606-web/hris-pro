package com.hris.rag.service;

import com.hris.rag.entity.Chunk;
import com.hris.rag.entity.DocVersion;
import com.hris.rag.entity.Document;
import com.hris.rag.mapper.DocVersionMapper;
import com.hris.rag.mapper.DocumentMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 命中切片的文档信息装配（chunk → doc_version → document 批量回查）。
 * 供 ChatService 组装 citations 与拒答提示标题，避免 N+1 查询。
 */
@Service
public class DocRefService {

    private final DocVersionMapper docVersionMapper;
    private final DocumentMapper documentMapper;

    public DocRefService(DocVersionMapper docVersionMapper, DocumentMapper documentMapper) {
        this.docVersionMapper = docVersionMapper;
        this.documentMapper = documentMapper;
    }

    /** 装配结果：chunkId -> 文档标题 / 文档ID。 */
    public record DocInfo(Map<Long, String> titles, Map<Long, Long> docIds) {
    }

    /** 批量回查文档标题与文档 ID（两级：kb_chunk → kb_doc_version → kb_document）。 */
    public DocInfo load(List<Chunk> chunks) {
        Map<Long, String> titles = new HashMap<>();
        Map<Long, Long> docIds = new HashMap<>();
        List<Long> docVersionIds = chunks.stream()
                .map(Chunk::getDocVersionId).filter(Objects::nonNull).toList();
        if (docVersionIds.isEmpty()) {
            return new DocInfo(titles, docIds);
        }
        Map<Long, Long> versionToDoc = new HashMap<>();
        for (DocVersion v : docVersionMapper.selectByIds(docVersionIds)) {
            versionToDoc.put(v.getId(), v.getDocumentId());
        }
        List<Long> docIdList = versionToDoc.values().stream()
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> docTitles = new HashMap<>();
        if (!docIdList.isEmpty()) {
            for (Document d : documentMapper.selectByIds(docIdList)) {
                docTitles.put(d.getId(), d.getTitle());
            }
        }
        for (Chunk c : chunks) {
            Long docId = versionToDoc.get(c.getDocVersionId());
            if (docId == null) {
                continue;
            }
            docIds.put(c.getId(), docId);
            String title = docTitles.get(docId);
            if (title != null) {
                titles.put(c.getId(), title);
            }
        }
        return new DocInfo(titles, docIds);
    }
}
