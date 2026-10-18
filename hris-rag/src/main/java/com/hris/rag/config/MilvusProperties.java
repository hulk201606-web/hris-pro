package com.hris.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 连接配置（docs/05 §1 向量库分租户）。
 *
 * <p>阶段 A：默认直连本机 Milvus standalone（内嵌 etcd + 本地存储，见 docker-compose.yml），
 * 阶段 B 接入 embedding 后按租户创建独立 Collection（别名 kb_{tenantId}）。</p>
 */
@ConfigurationProperties(prefix = "hris.rag.milvus")
public class MilvusProperties {

    /** Milvus gRPC 地址（host:port） */
    private String host = "127.0.0.1";
    private int port = 19530;
    /** 数据库名（Milvus 2.x 支持多库，默认 default） */
    private String database = "default";
    /** 连接超时（秒） */
    private long connectTimeoutSec = 10;
    /** 集合维度：默认 1024，对齐 text-embedding-v4（阶段 A 占位值 128 已废弃） */
    private int dimension = 1024;
    /** 向量字段名 */
    private String vectorField = "embedding";
    /** 主键字段名 */
    private String idField = "chunk_id";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public long getConnectTimeoutSec() {
        return connectTimeoutSec;
    }

    public void setConnectTimeoutSec(long connectTimeoutSec) {
        this.connectTimeoutSec = connectTimeoutSec;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public String getVectorField() {
        return vectorField;
    }

    public void setVectorField(String vectorField) {
        this.vectorField = vectorField;
    }

    public String getIdField() {
        return idField;
    }

    public void setIdField(String idField) {
        this.idField = idField;
    }
}
