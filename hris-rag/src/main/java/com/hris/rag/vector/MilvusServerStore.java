package com.hris.rag.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import com.hris.rag.config.MilvusProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.DescribeIndexResponse;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DescribeIndexParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milvus Server 实现（阶段 B：直连 standalone，内嵌 etcd + 本地存储）。
 *
 * <p>集合 schema：主键 chunk_id(BIGINT) + 向量字段 embedding(FLOAT_VECTOR) + 动态字段承载
 * space_id / acl_tags 等元数据（enableDynamicField=true）。建集合时同步建 COSINE + AUTOINDEX
 * 索引（缺索引无法 load/search）。向量维度由 {@code hris.rag.milvus.dimension} 配置（1024，
 * 对齐 text-embedding-v4）。</p>
 */
@Component
public class MilvusServerStore implements VectorStore {

    private final MilvusProperties props;
    private volatile MilvusServiceClient client;

    public MilvusServerStore(MilvusProperties props) {
        this.props = props;
    }

    private synchronized MilvusServiceClient client() {
        if (client == null) {
            ConnectParam param = ConnectParam.newBuilder()
                    .withHost(props.getHost())
                    .withPort(props.getPort())
                    .withDatabaseName(props.getDatabase())
                    .withConnectTimeout(props.getConnectTimeoutSec(), java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            client = new MilvusServiceClient(param);
        }
        return client;
    }

    @Override
    public void ensureCollection(String collectionName, int dimension) {
        MilvusServiceClient c = client();
        R<Boolean> has = c.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName).build());
        if (has.getStatus() != 0 || Boolean.FALSE.equals(has.getData())) {
            FieldType idField = FieldType.newBuilder()
                    .withName(props.getIdField())
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();
            FieldType vectorField = FieldType.newBuilder()
                    .withName(props.getVectorField())
                    .withDataType(DataType.FloatVector)
                    .withDimension(dimension)
                    .build();
            CreateCollectionParam param = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldTypes(List.of(idField, vectorField))
                    .withEnableDynamicField(true)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .build();
            R<RpcStatus> created = c.createCollection(param);
            if (created.getStatus() != 0) {
                throw new BizException(ErrorCode.RAG_ERROR, "向量集合创建失败: " + created.getMessage());
            }
            createVectorIndex(collectionName);
        }
    }

    /**
     * 建向量索引（幂等）。文本向量用 COSINE + AUTOINDEX（Milvus 2.5 standalone 免调参）；
     * 缺索引的集合无法 load/search（code=700 index not found）。
     */
    private void createVectorIndex(String collectionName) {
        MilvusServiceClient c = client();
        DescribeIndexParam describeParam = DescribeIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(props.getVectorField())
                .build();
        R<DescribeIndexResponse> described = c.describeIndex(describeParam);
        if (described.getStatus() == 0 && described.getData() != null
                && described.getData().getIndexDescriptionsCount() > 0) {
            return;
        }
        CreateIndexParam param = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(props.getVectorField())
                .withIndexType(IndexType.AUTOINDEX)
                .withMetricType(MetricType.COSINE)
                .build();
        R<RpcStatus> created = c.createIndex(param);
        if (created.getStatus() != 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "向量索引创建失败: " + created.getMessage());
        }
    }

    @Override
    public void upsert(String collectionName, long chunkId, List<Float> vector, Map<String, Object> metadata) {
        ensureCollection(collectionName, props.getDimension());
        List<JsonObject> rows = new ArrayList<>();
        JsonObject row = new JsonObject();
        row.addProperty(props.getIdField(), chunkId);
        row.add(props.getVectorField(), encodeVector(vector));
        for (Map.Entry<String, Object> e : metadata.entrySet()) {
            addMeta(row, e.getKey(), e.getValue());
        }
        rows.add(row);
        InsertParam param = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withRows(rows)
                .build();
        R<MutationResult> result = client().insert(param);
        if (result.getStatus() != 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "向量写入失败: " + result.getMessage());
        }
    }

    @Override
    public void delete(String collectionName, long chunkId) {
        DeleteParam param = DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(props.getIdField() + " == " + chunkId)
                .build();
        R<MutationResult> result = client().delete(param);
        if (result.getStatus() != 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "向量删除失败: " + result.getMessage());
        }
    }

    @Override
    public List<Long> search(String collectionName, List<Float> vector, int topK) {
        loadIfNeeded(collectionName);
        List<String> outFields = List.of(props.getIdField());
        SearchParam param = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.COSINE)
                .withTopK(topK)
                .withVectorFieldName(props.getVectorField())
                .withVectors(List.of(vector))
                .withOutFields(outFields)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();
        R<SearchResults> result = client().search(param);
        if (result.getStatus() != 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "向量检索失败: " + result.getMessage());
        }
        SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < wrapper.getIDScore(0).size(); i++) {
            Object id = wrapper.getFieldData(props.getIdField(), 0).get(i);
            if (id instanceof Number n) {
                ids.add(n.longValue());
            } else if (id != null) {
                ids.add(Long.parseLong(String.valueOf(id)));
            }
        }
        return ids;
    }

    @Override
    public List<SearchHit> searchWithScores(String collectionName, List<Float> vector, int topK) {
        loadIfNeeded(collectionName);
        List<String> outFields = List.of(props.getIdField());
        SearchParam param = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.COSINE)
                .withTopK(topK)
                .withVectorFieldName(props.getVectorField())
                .withVectors(List.of(vector))
                .withOutFields(outFields)
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .build();
        R<SearchResults> result = client().search(param);
        if (result.getStatus() != 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "向量检索失败: " + result.getMessage());
        }
        SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
        List<SearchHit> hits = new ArrayList<>();
        for (SearchResultsWrapper.IDScore is : wrapper.getIDScore(0)) {
            hits.add(new SearchHit(is.getLongID(), is.getScore()));
        }
        return hits;
    }

    @Override
    public void dropCollection(String collectionName) {
        DropCollectionParam param = DropCollectionParam.newBuilder()
                .withCollectionName(collectionName).build();
        R<RpcStatus> result = client().dropCollection(param);
        if (result.getStatus() != 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "集合删除失败: " + result.getMessage());
        }
    }

    /**
     * 加载集合到内存（幂等，检索前置条件）。新建/重建索引后集合处于未加载态，
     * 直接 search 会报 collection not loaded；已加载集合重复 load 返回成功。
     */
    private void loadIfNeeded(String collectionName) {
        MilvusServiceClient c = client();
        R<Boolean> has = c.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName).build());
        if (has.getStatus() != 0 || Boolean.FALSE.equals(has.getData())) {
            return;
        }
        R<RpcStatus> loaded = c.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName).build());
        if (loaded.getStatus() != 0) {
            throw new BizException(ErrorCode.RAG_ERROR, "向量集合加载失败: " + loaded.getMessage());
        }
    }

    private JsonArray encodeVector(List<Float> vector) {
        JsonArray arr = new JsonArray();
        for (Float v : vector) {
            arr.add(v);
        }
        return arr;
    }

    private void addMeta(JsonObject row, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number n) {
            row.addProperty(key, n);
        } else if (value instanceof Boolean b) {
            row.addProperty(key, b);
        } else {
            row.addProperty(key, String.valueOf(value));
        }
    }
}
