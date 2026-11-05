package com.hris.performance.service;

/**
 * 考核周期状态（PRF-F-002 状态机）。流转为严格单向白名单：
 * DRAFT → ACTIVE → SCORING → CLOSED，其余（含回退、跳级、自转）一律非法。
 *
 * <p>语义：
 * <ul>
 *   <li>DRAFT：配置期，周期/方案/目标/评价任务可编辑；</li>
 *   <li>ACTIVE：启动期，目标可调整、自评窗口开放；</li>
 *   <li>SCORING：评分期，目标冻结，上级/互评评分开放，自评窗口关闭；</li>
 *   <li>CLOSED：封板期，除申诉外全部写操作拒绝。</li>
 * </ul>
 *
 * <p>历史值兼容：docs/19 之前的库注释为 CALIBRATING/PUBLISHED 等，读侧统一归一到
 * 现行四态（PUBLISHED→CLOSED、CALIBRATING→SCORING、IN_PROGRESS→ACTIVE、
 * NOT_STARTED→DRAFT），避免存量数据因枚举扩展而穿透守卫。</p>
 */
public enum CycleStatus {

    /** 配置期 */
    DRAFT,
    /** 启动期（自评窗口） */
    ACTIVE,
    /** 评分期（自评窗口已关闭） */
    SCORING,
    /** 封板期 */
    CLOSED;

    /** 解析状态串，未知值返回 null（调用方按最严格口径处理）。 */
    public static CycleStatus of(String raw) {
        if (raw == null) {
            return null;
        }
        for (CycleStatus s : values()) {
            if (s.name().equalsIgnoreCase(raw.trim())) {
                return s;
            }
        }
        return null;
    }

    /** 解析状态串并兼容历史值；未知值按 CLOSED（最严格）处理。 */
    public static CycleStatus resolve(String raw) {
        CycleStatus s = of(raw);
        if (s != null) {
            return s;
        }
        if (raw == null) {
            return CLOSED;
        }
        return switch (raw.trim().toUpperCase()) {
            case "NOT_STARTED", "TODO" -> DRAFT;
            case "IN_PROGRESS", "RUNNING" -> ACTIVE;
            case "CALIBRATING" -> SCORING;
            case "PUBLISHED", "ARCHIVED" -> CLOSED;
            default -> CLOSED;
        };
    }

    /** 是否处于配置期（唯一允许改周期配置的状态）。 */
    public boolean configEditable() {
        return this == DRAFT;
    }

    /** 是否已封板（任何绩效数据写操作拒绝）。 */
    public boolean locked() {
        return this == CLOSED;
    }

    /** 白名单流转判定。 */
    public boolean canTransitionTo(CycleStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == ACTIVE;
            case ACTIVE -> target == SCORING;
            case SCORING -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}
