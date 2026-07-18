package com.astraflow.agent.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Decision} 枚举单元测试（moderation-hook「Decision 枚举」）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class DecisionTest {

    @Test
    @DisplayName("Decision 含 PASS/BLOCK/REDACT/HUMAN_REVIEW 四值")
    void testValues_containsFourDecisions() {
        assertEquals(4, Decision.values().length, "须含 4 个决策值");
        assertTrue(enumExists("PASS"));
        assertTrue(enumExists("BLOCK"));
        assertTrue(enumExists("REDACT"));
        assertTrue(enumExists("HUMAN_REVIEW"));
    }

    private boolean enumExists(String name) {
        for (Decision decision : Decision.values()) {
            if (decision.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
