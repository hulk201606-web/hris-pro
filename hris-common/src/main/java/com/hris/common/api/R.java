package com.hris.common.api;

import com.hris.common.exception.ErrorCode;
import com.hris.common.trace.TraceContext;

import java.io.Serializable;

/**
 * 统一响应体。格式遵循 docs/07 §1：{code, message, data, traceId, timestamp}。
 *
 * @param <T> 数据载荷类型
 */
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码：0=成功，非 0=错误（分段见 ErrorCode） */
    private int code;

    /** 人类可读消息；4xx 场景携带 messageKey 供前端 i18n */
    private String message;

    /** 数据载荷 */
    private T data;

    /** 链路追踪 ID（贯穿网关→服务→DB→MQ） */
    private String traceId;

    /** 响应时间戳（epoch millis，UTC） */
    private long timestamp;

    public R() {
    }

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = TraceContext.getTraceId();
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok() {
        return new R<>(0, "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(0, "success", data);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public boolean isSuccess() {
        return this.code == 0;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
