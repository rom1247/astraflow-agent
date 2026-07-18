package com.astraflow.agent;

import com.astraflow.agent.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * 应用上下文冒烟测试：验证 Spring 上下文在 Testcontainers + Flyway 空迁移下可正常启动。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class AstraflowAgentApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
