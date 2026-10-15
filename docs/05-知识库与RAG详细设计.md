# 05 知识库与 RAG 详细设计

> 服务：hris-rag　栈：Spring AI Alibaba + Milvus + Elasticsearch(BM25) + RocketMQ　决策依据：ADR-005

## 1. 总体管线

```
上传/URL导入(flow产物/管理员)                定时/手动重建
        │ hris-doc-uploaded(标记知识空间)          │
        ▼                                          ▼
 [Parser] Tika/Markdown/HTML + 表格识别(视觉模型OCR兜底)
        ▼
 [Chunker] 结构感知切片: 标题层级树→段落块(512±64 token, 重叠15%)
           表格→行级转述块; FAQ→问答对独立块; 块携带 metadata:
           {doc_id, version_id, space_id, heading_path, effective_from/to, acl_tags[], lang}
        ▼
 [Embedding] DashScope text-embedding (维度按模型版本固定) ──可切换 OpenAI 兼容 API
        ▼
 双写索引: Milvus(dense, Collection=tenant_{id}) + ES(bm25 倒排, 按租户路由)
        ▼
 [在线检索] Query改写(多轮→独立问) → hybrid 召回(dense top50 + bm25 top50, RRF融合)
           → ACL 过滤(milvus boolean expr / ES terms, 以 JWT 身份标签强制)
           → Rerank(gte-rerank 或 bge, top10) → 置信门控 → Prompt 组装(带引用编号)
           → LLM 流式生成(SSE) → 引用校验(答案句↔chunk 对齐) → 输出+溯源
```

## 2. 关键设计决策

| 主题 | 决策 | 理由 |
|---|---|---|
| 向量库分租户 | 每租户独立 Milvus Collection（别名 `kb_{tenantId}`） | 物理隔离强于纯 metadata 过滤；租户数 > 阈值后评估 Partition Key 方案 |
| 混合检索 | dense + BM25 必须并存 | HR 制度号/假勤名词精确命中靠 BM25；语义靠 dense |
| 权限模型 | 检索链路强制 ACL-aware：用户标签快照（Redis，登录+权限变更事件刷新） × chunk acl_tags 求交 | 见 KBD-F-007；无标签=空间公开成员可见 |
| 时效性 | 版本化 + effective_from/to 过滤，"现行制度"仅召回生效版 | 员工问答答错旧政策是最大事故源 |
| 置信门控 | rerank 分数阈值 + 覆盖度检查（问题实体是否出现在召回块）→ 不足则拒答 | KBD-F-011 硬要求 |
| 幻觉防线 | 引用强绑定：每答案句必须映射 chunk，映射失败句子丢弃/标注不确定 | 溯源可点击验证 |
| 个性化 | Function-calling 只读工具：查本人假期额度(ATT)/流程进度(FLW)/档案摘要(EMP)，服务端按工具白名单+本人身份执行 | KBD-F-010；LLM 不得持写权限 |
| 敏感隔离 | 薪酬数字、证件号 PII 进模型前占位符脱敏（可还原层在展示端） | 08 合规要求 |
| 成本控制 | 会话 5 轮裁剪、缓存(问题归一 hash→答案 TTL 24h)、路由小模型做改写/大模型做生成 | NFR 09 单请求成本目标 |

## 3. 数据模型（rag_db）

- kb_document / kb_doc_version（状态机：草稿→待审→已发布→已失效→已归档）
- kb_chunk（doc_version_id, seq, heading_path, text, token_cnt, milvus_id, es_id, acl_tags json）
- kb_space / kb_space_member / kb_acl_tag（标签字典：密级/部门/角色）
- qa_session / qa_message（question, answer, refs json, tool_calls json, latency_ms, model）
- qa_feedback（message_id, type, correction_text）→ kb_govern_ticket（治理工单）
- kb_eval_set / kb_eval_case / kb_eval_run（指标快照）

## 4. 评测体系

- 离线：评测集（上线前 ≥ 500 问，覆盖 9 大模块制度），指标 Recall@10 ≥ 0.9、答案忠实度(RAGAS faithfulness) ≥ 0.95、
  首答准确率（人工判）≥ 85%；每次切片/模型/Prompt 变更跑回归（CI 门禁输出对比报告）。
- 在线：点踩率、零召回率、拒答率、平均引用点击验证率看板（KBD-F-013）。

## 5. 运维与演进

- 索引重建按租户灰度（KBD-F-018），双 Collection 切换（蓝绿别名）。
- 模型供应商适配层：Embedding/Rerank/Chat 三类接口抽象，配置中心切换带冒烟自检。
- 后续演进预留：知识图谱（组织-制度-流程实体关系）、语音问答入口、主动推送（新制度触达相关人群）。
