package com.astraflow.agent.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModerationBlockedException} 单元测试（moderation-hook BLOCK 信号异常）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class ModerationBlockedExceptionTest {

    @Test
    @DisplayName("异常 - 为 RuntimeException 子类且携带中文消息")
    void testException_isRuntimeExceptionWithChineseMessage() {
        ModerationBlockedException exception = new ModerationBlockedException("工具入参被审核拦截");

        assertInstanceOf(RuntimeException.class, exception, "须为 RuntimeException 子类");
        assertTrue(exception.getMessage().contains("审核拦截"), "消息为中文");
    }

    @Test
    @DisplayName("serialVersionUID - 已声明")
    void testSerialVersionUid_declared() throws Exception {
        var field = ModerationBlockedException.class.getDeclaredField("serialVersionUID");
        field.setAccessible(true);

        assertEquals(1L, field.get(null), "@Serial serialVersionUID = 1L");
    }
}
