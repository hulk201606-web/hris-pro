package com.hris.rag.vector;

import java.util.List;
import java.util.Map;

/**
 * 向量存储抽象（docs/05 §2 向量库分租户 + §5 模型供应商可切换）。
 *
 * <p>阶段 A：抽象向量库读写，屏蔽 Milvus Server / Milvus-Lite 差异；
 * 阶段 B 接入 embedding 后由调用方传入真实向量，当前以占位向量验证读写链路。</p>
 */
public interface VectorStore {

    /**
     * 确保集合存在（幂等）。集合按租户隔离：collectionName 形如 kb_{tenantId}。
     *
     * @param collectionName 集合名
     * @param dimension      向量维度（由 embedding 模型确定）
     */
    void ensureCollection(String collectionName, int dimension);

    /**
     * 写入/更新一条向量记录（幂等 upsert）。
     *
     * @param collectionName 集合名
     * @param chunkId        切片主键（与 kb_chunk.id 对齐）
     * @param vector         向量（阶段 A 为占位向量）
     * @param metadata       业务元数据（space_id / acl_tags 等，用于 ACL 过滤）
     */
    void upsert(String collectionName, long chunkId, List<Float> vector, Map<String, Object> metadata);

    /**
     * 按主键删除。
     *
     * @param collectionName 集合名
     * @param chunkId        切片主键
     */
    void delete(String collectionName, long chunkId);

    /**
     * 向量相似度检索（Top-K）。
     *
     * @param collectionName 集合名
     * @param vector         查询向量
     * @param topK           返回条数
     * @return 命中切片主键列表（按相似度降序）
     */
    List<Long> search(String collectionName, List<Float> vector, int topK);

    /**
     * 删除集合（重建索引时蓝绿切换用，阶段 C）。
     *
     * @param collectionName 集合名
     */
    void dropCollection(String collectionName);
}
