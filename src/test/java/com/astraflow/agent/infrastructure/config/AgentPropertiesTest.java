package com.astraflow.agent.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link AgentProperties} 配置绑定单元测试。
 *
 * <p>验证 {@code @ConfigurationProperties(prefix="agent")} 可绑定最大轮次 / 上下文预算 / 保留轮数，
 * 且默认值符合 spec context-truncation「阈值来源于配置」（contextBudgetTokens=8000、keepRounds=4、maxTurns=10）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
class AgentPropertiesTest {

    @Test
    @DisplayName("默认值 - 未配置时取约定默认")
    void testDefaults_whenNotConfigured_useConventionDefaults() {
        AgentProperties properties = new AgentProperties();

        assertEquals(10, properties.getMaxTurns(), "maxTurns 默认 10");
        assertEquals(8000, properties.getContextBudgetTokens(), "contextBudgetTokens 默认 8000");
        assertEquals(4, properties.getKeepRounds(), "keepRounds 默认 4");
    }

    @Test
    @DisplayName("绑定 - setter 可写入并由 getter 读出")
    void testBinding_whenSettersCalled_valuesReflected() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxTurns(5);
        properties.setContextBudgetTokens(4096);
        properties.setKeepRounds(2);

        assertEquals(5, properties.getMaxTurns());
        assertEquals(4096, properties.getContextBudgetTokens());
        assertEquals(2, properties.getKeepRounds());
    }

    @Test
    @DisplayName("前缀 - 标注 @ConfigurationProperties(prefix=\"agent\")")
    void testAnnotation_declaresAgentPrefix() {
        ConfigurationProperties annotation = AgentProperties.class.getAnnotation(ConfigurationProperties.class);

        assertNotNull(annotation, "AgentProperties 须标注 @ConfigurationProperties");
        assertEquals("agent", annotation.prefix(), "前缀为 agent");
    }
}
