package com.hris.performance.service;

import com.hris.performance.service.ScoreVisibilityRule.Stage;
import com.hris.performance.service.ScoreVisibilityRule.Viewer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 互见规则矩阵单测（PRF-F-004）：请求者角色 × 周期阶段 × 分数归属。
 *
 * <p>核心断言：自评分在 PREPARING 阶段对上级/HR 不可见（自评窗口关闭 = 进入 SCORING）；
 * 上级评分对员工仅在 CLOSED 可见；作者本人任何时候可见自己写的评价。</p>
 */
@DisplayName("评价过程分互见规则")
class ScoreVisibilityRuleTest {

    private static final String SELF = "SELF";
    private static final String SUPERVISOR = "SUPERVISOR";

    @Nested
    @DisplayName("阶段映射")
    class StageMapping {

        @Test
        @DisplayName("DRAFT/ACTIVE → PREPARING，SCORING → SCORING，CLOSED → CLOSED")
        void mapsCurrentStatuses() {
            assertEquals(Stage.PREPARING, ScoreVisibilityRule.stageOf("DRAFT"));
            assertEquals(Stage.PREPARING, ScoreVisibilityRule.stageOf("ACTIVE"));
            assertEquals(Stage.SCORING, ScoreVisibilityRule.stageOf("SCORING"));
            assertEquals(Stage.CLOSED, ScoreVisibilityRule.stageOf("CLOSED"));
        }

        @Test
        @DisplayName("历史状态归一：CALIBRATING→SCORING，PUBLISHED→CLOSED；未知→CLOSED（不披露），null→PREPARING")
        void mapsLegacyStatuses() {
            assertEquals(Stage.SCORING, ScoreVisibilityRule.stageOf("CALIBRATING"));
            assertEquals(Stage.CLOSED, ScoreVisibilityRule.stageOf("PUBLISHED"));
            assertEquals(Stage.PREPARING, ScoreVisibilityRule.stageOf(null));
            assertEquals(Stage.CLOSED, ScoreVisibilityRule.stageOf("WEIRD"));
        }
    }

    @Nested
    @DisplayName("视角解析")
    class ViewerResolution {

        private static final Long ME = 100L;
        private static final Long AUTHOR = 200L;
        private static final Long EMP = 300L;

        @Test
        @DisplayName("作者优先于 HR：HR 本人写的评价仍按作者可见；非作者时按 HR 视角")
        void authorBeforeHr() {
            assertEquals(Viewer.AUTHOR, ScoreVisibilityRule.viewerOf(ME, ME, EMP,
                    Set.of("prf:cycle:write")));
            assertEquals(Viewer.HR, ScoreVisibilityRule.viewerOf(ME, AUTHOR, EMP,
                    Set.of("prf:cycle:write")));
        }

        @Test
        @DisplayName("作者 > 被评人 > 上级")
        void precedence() {
            assertEquals(Viewer.AUTHOR, ScoreVisibilityRule.viewerOf(AUTHOR, AUTHOR, EMP, Set.of()));
            assertEquals(Viewer.EMPLOYEE, ScoreVisibilityRule.viewerOf(EMP, AUTHOR, EMP, Set.of()));
            assertEquals(Viewer.SUPERVISOR, ScoreVisibilityRule.viewerOf(ME, AUTHOR, EMP, Set.of()));
        }

        @Test
        @DisplayName("无登录身份：按被评人（fail-closed）")
        void nullRequesterIsEmployee() {
            assertEquals(Viewer.EMPLOYEE, ScoreVisibilityRule.viewerOf(null, AUTHOR, EMP, Set.of()));
            assertFalse(ScoreVisibilityRule.isHr(null));
        }
    }

    @Nested
    @DisplayName("三角色 × 三阶段矩阵")
    class Matrix {

        @Test
        @DisplayName("员工（被评人）：自评恒可见；上级评分仅 CLOSED 可见")
        void employeeRow() {
            assertTrue(visible(Viewer.EMPLOYEE, SELF, Stage.PREPARING));
            assertTrue(visible(Viewer.EMPLOYEE, SELF, Stage.SCORING));
            assertTrue(visible(Viewer.EMPLOYEE, SELF, Stage.CLOSED));

            assertFalse(visible(Viewer.EMPLOYEE, SUPERVISOR, Stage.PREPARING));
            assertFalse(visible(Viewer.EMPLOYEE, SUPERVISOR, Stage.SCORING));
            assertTrue(visible(Viewer.EMPLOYEE, SUPERVISOR, Stage.CLOSED));
        }

        @Test
        @DisplayName("直属上级：自评在自评窗口关闭（SCORING）后才可见；上级评分不受限")
        void supervisorRow() {
            assertFalse(visible(Viewer.SUPERVISOR, SELF, Stage.PREPARING));
            assertTrue(visible(Viewer.SUPERVISOR, SELF, Stage.SCORING));
            assertTrue(visible(Viewer.SUPERVISOR, SELF, Stage.CLOSED));

            assertTrue(visible(Viewer.SUPERVISOR, SUPERVISOR, Stage.PREPARING));
            assertTrue(visible(Viewer.SUPERVISOR, SUPERVISOR, Stage.SCORING));
            assertTrue(visible(Viewer.SUPERVISOR, SUPERVISOR, Stage.CLOSED));
        }

        @Test
        @DisplayName("HR：自评同样需等窗口关闭；上级评分（校准）任何阶段可见")
        void hrRow() {
            assertFalse(visible(Viewer.HR, SELF, Stage.PREPARING));
            assertTrue(visible(Viewer.HR, SELF, Stage.SCORING));
            assertTrue(visible(Viewer.HR, SELF, Stage.CLOSED));

            assertTrue(visible(Viewer.HR, SUPERVISOR, Stage.PREPARING));
            assertTrue(visible(Viewer.HR, SUPERVISOR, Stage.CLOSED));
        }

        @Test
        @DisplayName("作者本人：任何阶段可见自己写的评价")
        void authorRow() {
            for (Stage stage : Stage.values()) {
                assertTrue(visible(Viewer.AUTHOR, SELF, stage));
                assertTrue(visible(Viewer.AUTHOR, SUPERVISOR, stage));
            }
        }

        @Test
        @DisplayName("互评/下级评分：对外部员工披露口径与上级评分一致（仅 CLOSED）")
        void peerRow() {
            assertFalse(visible(Viewer.EMPLOYEE, "PEER", Stage.SCORING));
            assertTrue(visible(Viewer.EMPLOYEE, "PEER", Stage.CLOSED));
            assertTrue(visible(Viewer.HR, "SUBORDINATE", Stage.SCORING));
        }
    }

    private boolean visible(Viewer viewer, String role, Stage stage) {
        return ScoreVisibilityRule.scoresVisible(viewer, role, stage);
    }
}
