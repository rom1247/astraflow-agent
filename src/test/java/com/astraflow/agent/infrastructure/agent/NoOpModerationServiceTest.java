package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.model.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link NoOpModerationService} 默认放行实现单元测试（moderation-hook「默认实现全部放行」）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class NoOpModerationServiceTest {

    @Test
    @DisplayName("审核 - 任意内容均返回 PASS")
    void testModerate_anyContent_returnsPass() {
        NoOpModerationService service = new NoOpModerationService();

        assertEquals(Decision.PASS, service.moderate("你好", "agent"),
                "agent 节点入参放行");
        assertEquals(Decision.PASS, service.moderate("{\"a\":1}", "tool"),
                "工具入参放行");
        assertEquals(Decision.PASS, service.moderate("敏感内容", "output"),
                "输出放行");
    }
}
