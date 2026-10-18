package com.hris.common.exception;

/**
 * 错误码分段（遵循 docs/07 §2）。分段：0 成功；1xxxx 通用；2xxxx 认证授权；
 * 3xxxx 流程；4xxxx RAG；5xxxx 业务域；50000+ 系统内部错误。
 */
public enum ErrorCode {

    /* ---------- 通用 10000-10999 ---------- */
    PARAM_INVALID(10001, "参数校验失败"),
    NOT_FOUND(10002, "资源不存在"),
    IDEMPOTENT_CONFLICT(10003, "幂等冲突，请勿重复提交"),
    RATE_LIMITED(10429, "请求过于频繁，请稍后再试"),

    /* ---------- 认证授权 20000-29999 ---------- */
    UNAUTHORIZED(20401, "未认证或凭证已失效"),
    FORBIDDEN(20403, "无权限访问该资源"),
    SECOND_AUTH_REQUIRED(20410, "敏感操作需要二次认证"),
    TENANT_INVALID(20501, "租户已失效或不存在"),

    /* ---------- 流程 30000-39999 ---------- */
    FLOW_NOT_FOUND(30404, "流程不存在"),
    FLOW_STATE_CONFLICT(30409, "流程状态冲突"),
    FLOW_NO_PERMISSION(30423, "无权处理该流程任务"),

    /* ---------- RAG 40000-49999 ---------- */
    RAG_PROVIDER_DOWN(40050, "模型服务暂不可用，已降级"),
    RAG_NO_EVIDENCE(40060, "未找到相关依据，请咨询 HR"),
    RAG_ERROR(49000, "知识库域错误"),

    /* ---------- 业务域 5xxxx ---------- */
    ORG_ERROR(50000, "组织域错误"),
    EMP_ERROR(51000, "员工域错误"),
    EMP_HEADCOUNT_SHORT(51204, "编制不足"),
    ATT_ERROR(52000, "假勤域错误"),
    RCS_ERROR(53000, "招聘域错误"),
    CMP_ERROR(54000, "薪酬域错误"),
    CMP_SALARY_FORBIDDEN(54001, "薪酬密域无权限"),
    PRF_ERROR(55000, "绩效域错误"),

    /* ---------- 系统 50000+ ---------- */
    SYSTEM_ERROR(50001, "系统内部错误，请提供 traceId 联系管理员");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
