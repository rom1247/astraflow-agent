package com.astraflow.agent.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EventType} 枚举完整性测试。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class EventTypeTest {

    @Test
    @DisplayName("EventType 含且仅含 7 种事件类型")
    void testEventType_containsExactlySevenValues() {
        assertThat(EventType.values()).containsExactly(
                EventType.SESSION_START,
                EventType.ASSISTANT_TEXT,
                EventType.TOOL_USE,
                EventType.TOOL_RESULT,
                EventType.TURN_END,
                EventType.DONE,
                EventType.ERROR);
    }
}
