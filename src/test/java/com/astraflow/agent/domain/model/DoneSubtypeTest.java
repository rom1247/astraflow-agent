package com.astraflow.agent.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DoneSubtype} 枚举单元测试（agent-loop「subtype 以枚举承载」）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class DoneSubtypeTest {

    @Test
    @DisplayName("subtype 枚举含 7 个命名常量且 wire 值正确")
    void testValues_containsSevenConstantsWithWireValues() {
        Set<String> wireValues = Arrays.stream(DoneSubtype.values())
                .map(DoneSubtype::getValue)
                .collect(Collectors.toSet());

        assertEquals(7, DoneSubtype.values().length, "须含 7 个常量");
        Set<String> expected = Set.of(
                "success",
                "error_during_execution",
                "error_max_turns",
                "error_canceled",
                "error_blocked_input",
                "error_blocked_output",
                "error_blocked_tool");
        assertEquals(expected, wireValues, "wire 值集合与协议一致");
    }

    @Test
    @DisplayName("协议预留值可被引用（不产出但不报错）")
    void testReservedValues_referencedWithoutError() {
        // error_canceled / error_blocked_* 为 #5/#7 预留，本 change 仅引用确保枚举承载而非魔法字符串
        assertTrue(DoneSubtype.ERROR_CANCELED.getValue().startsWith("error_"));
        assertTrue(DoneSubtype.ERROR_BLOCKED_TOOL.getValue().contains("tool"));
    }
}
