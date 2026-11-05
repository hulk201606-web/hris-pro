package com.hris.performance.service;

import java.util.Objects;
import java.util.Set;

/**
 * 评价过程分互见规则（PRF-F-004 防博弈）。纯函数、无 Spring 依赖，便于矩阵化单测。
 *
 * <p>规则（服务端掩码口径，越权视角只置 null 不删字段）：
 * <ol>
 *   <li>作者看自己写的评价：任何阶段可见；</li>
 *   <li>自评分（SELF）：直属上级 / HR 需等自评窗口关闭（进入 SCORING）才可见；</li>
 *   <li>上级评分（SUPERVISOR/PEER/SUBORDINATE）：被评人需等周期 CLOSED（结果公布）才可见；</li>
 *   <li>上级/HR 看非自评过程分不受限制（规格 §4：仅 HRBP/校准环节可见）。</li>
 * </ol>
 *
 * <p>周期阶段映射：DRAFT/ACTIVE → PREPARING（自评窗口内），SCORING → SCORING（自评窗口已关闭），
 * CLOSED → CLOSED。历史状态经 {@link CycleStatus#resolve} 归一，未知值按最严格阶段（CLOSED）处理，
 * 即"认不出状态就不披露"。</p>
 */
public final class ScoreVisibilityRule {

    /** 视为 HR 视角的权限点（周期配置/评价管理/校准）。 */
    private static final Set<String> HR_PERMS =
            Set.of("prf:cycle:write", "prf:evaluation:admin", "prf:calibration:write");

    private ScoreVisibilityRule() {
    }

    /** 周期阶段（决定互见窗口）。 */
    public enum Stage {
        /** DRAFT/ACTIVE：自评窗口内，他人过程分一律不可见 */
        PREPARING,
        /** SCORING：自评窗口已关闭，上级/HR 可见自评分 */
        SCORING,
        /** CLOSED：结果公布，被评人可见上级评分 */
        CLOSED
    }

    /** 请求者视角。 */
    public enum Viewer {
        /** 该条评价的作者（评价人本人） */
        AUTHOR,
        /** 被评人（员工本人） */
        EMPLOYEE,
        /** 直属上级 / 其他管理者视角 */
        SUPERVISOR,
        /** HR / HRBP 视角 */
        HR
    }

    /** 周期状态 → 阶段。null 或空白按 PREPARING（最严格自评窗口）；未知状态按 CLOSED（不披露）。 */
    public static Stage stageOf(String cycleStatus) {
        if (cycleStatus == null || cycleStatus.isBlank()) {
            return Stage.PREPARING;
        }
        return switch (CycleStatus.resolve(cycleStatus)) {
            case DRAFT, ACTIVE -> Stage.PREPARING;
            case SCORING -> Stage.SCORING;
            case CLOSED -> Stage.CLOSED;
        };
    }

    /**
     * 解析请求者视角：作者优先（自己写的评价永远可见），其次 HR（管理视角），
     * 其次被评人，其余按上级视角。requesterId 缺失时按被评人视角（fail-closed）。
     */
    public static Viewer viewerOf(Long requesterId, Long authorId, Long empId, Set<String> perms) {
        if (requesterId == null) {
            return Viewer.EMPLOYEE;
        }
        if (Objects.equals(requesterId, authorId)) {
            return Viewer.AUTHOR;
        }
        if (isHr(perms)) {
            return Viewer.HR;
        }
        if (Objects.equals(requesterId, empId)) {
            return Viewer.EMPLOYEE;
        }
        return Viewer.SUPERVISOR;
    }

    /** 是否 HR 视角（按权限点判定，角色名各租户不统一，不用 roles 硬编码）。 */
    public static boolean isHr(Set<String> perms) {
        if (perms == null || perms.isEmpty()) {
            return false;
        }
        for (String p : HR_PERMS) {
            if (perms.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 某条评价的评分数值/评语是否对请求者可见。
     *
     * @param viewer         请求者视角
     * @param scoreOwnerRole 该条评价的评价人角色（EvalTask.evaluatorRole）
     * @param stage          周期阶段
     */
    public static boolean scoresVisible(Viewer viewer, String scoreOwnerRole, Stage stage) {
        if (viewer == null) {
            return false;
        }
        Stage s = stage == null ? Stage.PREPARING : stage;
        if (viewer == Viewer.AUTHOR) {
            return true;
        }
        if (isSelf(scoreOwnerRole)) {
            // 自评分：被评人本人恒可见（自评由其本人提交）；上级/HR 需等自评窗口关闭
            return viewer == Viewer.EMPLOYEE || s != Stage.PREPARING;
        }
        if (viewer == Viewer.EMPLOYEE) {
            // 上级/互评/下级评分：被评人仅结果公布后可见
            return s == Stage.CLOSED;
        }
        return true;
    }

    private static boolean isSelf(String role) {
        return role != null && "SELF".equalsIgnoreCase(role.trim());
    }
}
