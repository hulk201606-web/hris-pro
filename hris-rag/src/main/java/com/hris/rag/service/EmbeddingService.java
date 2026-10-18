package com.hris.rag.service;

import com.hris.rag.config.MilvusProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本向量化服务（阶段 B：接入 Spring AI 2.0 EmbeddingModel）。
 *
 * <p>通义千问 text-embedding-v4 走 DashScope OpenAI 兼容端点（ADR-005 修订）。
 * 未配置 DASHSCOPE_API_KEY 时降级到确定性占位向量，保证服务可启动、索引重建链路可验证；
 * 配置 Key 后自动切换真实语义向量。</p>
 */
@Service
public class EmbeddingService {

    private final MilvusProperties props;
    private final EmbeddingModel embeddingModel;
    private final boolean keyPresent;

    public EmbeddingService(ObjectProvider<EmbeddingModel> embeddingProvider, MilvusProperties props,
                            ObjectProvider<OpenAiCommonProperties> commonPropsProvider) {
        this.props = props;
        this.embeddingModel = embeddingProvider.getIfAvailable();
        // OpenAiEmbeddingModel bean 在缺 Key 时仍会创建（matchIfMissing=true），
        // 因此以 api-key 是否为空作为是否走真实 embedding 的依据，而非 bean 是否存在。
        OpenAiCommonProperties common = commonPropsProvider.getIfAvailable();
        String key = common == null ? null : common.getApiKey();
        this.keyPresent = key != null && !key.isBlank();
    }

    /** 是否已接入真实 embedding（配置了 Key）。 */
    public boolean isReady() {
        return keyPresent && embeddingModel != null;
    }

    /**
     * 单条文本向量化。未就绪时返回占位向量（与 chunkId 无关，仅保证维度一致）。
     *
     * @param text 文本
     * @param seed 确定性种子（占位向量用；真实 embedding 忽略）
     */
    public List<Float> embed(String text, long seed) {
        if (isReady()) {
            float[] vec = embeddingModel.embed(text == null ? "" : text);
            List<Float> out = new ArrayList<>(vec.length);
            for (float v : vec) {
                out.add(v);
            }
            return out;
        }
        return placeholderVector(seed);
    }

    /**
     * 阶段 A 占位向量：由 seed 派生确定性向量（非语义，仅验证链路）。
     * 配置 DASHSCOPE_API_KEY 后 {@link #embed} 走真实模型，此方法不再使用。
     */
    private List<Float> placeholderVector(long seed) {
        int dim = props.getDimension();
        List<Float> v = new ArrayList<>(dim);
        long s = seed;
        for (int i = 0; i < dim; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            double d = ((s >>> 11) & 0xFFFFF) / 1048576.0;
            v.add((float) d);
        }
        return v;
    }
}
