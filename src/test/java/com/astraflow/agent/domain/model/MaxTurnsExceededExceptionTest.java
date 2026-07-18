package com.astraflow.agent.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MaxTurnsExceededException} 单元测试（agent-loop 最大轮次信号异常）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class MaxTurnsExceededExceptionTest {

    @Test
    @DisplayName("异常 - 为 RuntimeException 子类且携带中文消息")
    void testException_isRuntimeExceptionWithChineseMessage() {
        MaxTurnsExceededException exception = new MaxTurnsExceededException("已达最大轮次: 10");

        assertInstanceOf(RuntimeException.class, exception, "须为 RuntimeException 子类");
        assertTrue(exception.getMessage().contains("轮次"), "消息为中文");
    }

    @Test
    @DisplayName("serialVersionUID - 已声明")
    void testSerialVersionUid_declared() throws Exception {
        var field = MaxTurnsExceededException.class.getDeclaredField("serialVersionUID");
        field.setAccessible(true);

        assertEquals(1L, field.get(null), "@Serial serialVersionUID = 1L");
    }
}
