package com.hris.employee.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EmployeeStatus 单元测试（docs/03-02 §4 状态机，需求 EMP-F-018 优先级 P0）。
 *
 * <p>本类是全批次最高价值目标：canTransitionTo 是 8×8 完整流转表，被EmployeeStateMachine
 * 作为唯一状态变更入口调用。一旦某条transition 分支被放宽，非法流转（如已离职员工直接
 * 回到在职）不会被任何其他测试拦下，故此处对全表逐格断言，而非抽样。</p>
 */
class EmployeeStatusTest {

    @Nested
    @DisplayName("canTransitionTo：合法流转全表")
    class Legal {

        @ParameterizedTest(name = "{0} -> {1} 允许流转")
        @CsvSource({
            "PENDING,    PROBATION",
            "PENDING,    CANCELLED",
            "PROBATION,  ACTIVE",
            "PROBATION,  RESIGNING",
            "PROBATION,  BLACKLISTED",
            "ACTIVE,     RESIGNING",
            "ACTIVE,     RETIRED",
            "ACTIVE,     BLACKLISTED",
            "RESIGNING,  RESIGNED",
            "RESIGNING,  ACTIVE",
            "RESIGNED,   BLACKLISTED"
        })
        @DisplayName("需求定义的合法流转一律放行")
        void allowsDocumentedTransitions(EmployeeStatus from, EmployeeStatus to) {
            assertTrue(from.canTransitionTo(to),
                    from + " -> " + to + " 应为合法流转");
        }
    }

    @Nested
    @DisplayName("canTransitionTo：非法流转")
    class Illegal {

        @ParameterizedTest(name = "{0} -X-> {1} 拦截")
        @CsvSource({
            "PENDING,    ACTIVE",
            "PENDING,    RESIGNING",
            "PENDING,    RESIGNED",
            "PENDING,    RETIRED",
            "PENDING,    BLACKLISTED",
            "PROBATION,  PENDING",
            "PROBATION,  RESIGNED",
            "PROBATION,  RETIRED",
            "ACTIVE,     PENDING",
            "ACTIVE,     PROBATION",
            "ACTIVE,     RESIGNED",
            "RESIGNING,  PENDING",
            "RESIGNING,  CANCELLED",
            "RESIGNING,  RETIRED",
            "RESIGNED,   PENDING",
            "RESIGNED,   ACTIVE",
            "RESIGNED,   RESIGNING",
            "RESIGNED,   RETIRED"
        })
        @DisplayName("需求未定义的流转一律拦截")
        void blocksUndefinedTransitions(EmployeeStatus from, EmployeeStatus to) {
            assertFalse(from.canTransitionTo(to),
                    from + " -> " + to + " 应被判为非法流转");
        }

        @Test
        @DisplayName("流转到自身一律非法（自环不应被视作有效变更）")
        void blocksSelfTransition() {
            for (EmployeeStatus s : EmployeeStatus.values()) {
                assertFalse(s.canTransitionTo(s), s + " -> 自身 应为非法");
            }
        }

        @Test
        @DisplayName("流转到 null 一律非法，且不抛 NPE")
        void blocksNullTarget() {
            for (EmployeeStatus s : EmployeeStatus.values()) {
                assertFalse(s.canTransitionTo(null), s + " -> null 应为非法");
            }
        }

        @Test
        @DisplayName("已离职不可回到在职（防止离职回滚造成在职人数虚高）")
        void blocksResignedBackToActive() {
            assertFalse(EmployeeStatus.RESIGNED.canTransitionTo(EmployeeStatus.ACTIVE));
        }
    }

    @Nested
    @DisplayName("终态：isTerminal 判定与出边约束")
    class Terminal {

        @ParameterizedTest(name = "{0} 是终态")
        @EnumSource(value = EmployeeStatus.class,
                names = {"RESIGNED", "CANCELLED", "RETIRED", "BLACKLISTED"})
        @DisplayName("4 个状态被识别为终态（不可再回到在职）")
        void identifiesTerminalStates(EmployeeStatus s) {
            assertTrue(s.isTerminal(), s + " 应被识别为终态");
            assertFalse(s.canTransitionTo(EmployeeStatus.ACTIVE),
                    s + " 是终态，不应能回到在职");
            assertFalse(s.canTransitionTo(EmployeeStatus.PROBATION),
                    s + " 是终态，不应能回到试用");
        }

        @ParameterizedTest(name = "{0} 对所有目标均无出边")
        @EnumSource(value = EmployeeStatus.class,
                names = {"CANCELLED", "RETIRED", "BLACKLISTED"})
        @DisplayName("已取消/退休/黑名单为绝对终态，无任何出边")
        void absoluteTerminalHasNoOutgoingTransition(EmployeeStatus terminal) {
            for (EmployeeStatus target : EmployeeStatus.values()) {
                assertFalse(terminal.canTransitionTo(target),
                        "绝对终态 " + terminal + " 不应能流转到 " + target);
            }
        }

        @Test
        @DisplayName("已离职是终态，但保留唯一出边 → 黑名单（拉黑需覆盖离职员工）")
        void resignedIsTerminalButAllowsBlacklist() {
            assertTrue(EmployeeStatus.RESIGNED.isTerminal());
            assertTrue(EmployeeStatus.RESIGNED.canTransitionTo(EmployeeStatus.BLACKLISTED),
                    "离职员工仍需可被拉黑，故此出边为需求有意保留，不可误当缺陷删除");
        }

        @ParameterizedTest(name = "{0} 非终态")
        @EnumSource(value = EmployeeStatus.class,
                names = {"PENDING", "PROBATION", "ACTIVE", "RESIGNING"})
        @DisplayName("非终态仍可继续流转")
        void nonTerminalCanStillTransition(EmployeeStatus s) {
            assertFalse(s.isTerminal(), s + " 不应被识别为终态");
        }
    }

    @Nested
    @DisplayName("fromCode：code 反查枚举")
    class FromCode {

        @ParameterizedTest(name = "code {0} -> {1}")
        @CsvSource({
            "0, PENDING",
            "1, PROBATION",
            "2, ACTIVE",
            "3, RESIGNING",
            "4, RESIGNED",
            "5, CANCELLED",
            "6, RETIRED",
            "7, BLACKLISTED"
        })
        @DisplayName("8 个持久化 code 正确反查（落库 TINYINT，码值不可错位）")
        void mapsCodeToEnum(Integer code, EmployeeStatus expected) {
            assertEquals(expected, EmployeeStatus.fromCode(code));
        }

        @Test
        @DisplayName("null code → null（调用方据此抛「员工状态非法」）")
        void nullCodeReturnsNull() {
            assertNull(EmployeeStatus.fromCode(null));
        }

        @ParameterizedTest(name = "越界 code {0} → null")
        @ValueSource(ints = {-1, 8, 99, 127})
        @DisplayName("越界 code → null，不抛异常（脏数据不导致流程崩溃）")
        void unknownCodeReturnsNull(Integer code) {
            assertNull(EmployeeStatus.fromCode(code), "未知 code 应返回 null，实际 code=" + code);
        }

        @Test
        @DisplayName("code 与枚举定义一一对应，防止落库值与状态机脱节")
        void codeMatchesEnumOrdinalContract() {
            for (EmployeeStatus s : EmployeeStatus.values()) {
                assertEquals(s, EmployeeStatus.fromCode(s.getCode()),
                        s.name() + " 的 code 与反查结果不一致");
            }
        }
    }

    @Nested
    @DisplayName("元数据")
    class Meta {

        @Test
        @DisplayName("中文标签非空（用于异常信息拼接）")
        void labelsArePresent() {
            for (EmployeeStatus s : EmployeeStatus.values()) {
                assertTrue(s.getLabel() != null && !s.getLabel().isBlank(),
                        s.name() + " 缺少中文标签");
            }
        }
    }
}