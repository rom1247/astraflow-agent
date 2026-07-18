package com.astraflow.agent.persistence;

import com.astraflow.agent.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 整体内省：断言 {@code spring.jpa.hibernate.ddl-auto=validate} 下全部 {@code @Entity} 映射与
 * {@code V1__init.sql} schema 一致。
 *
 * <p>{@code @SpringBootTest} 上下文成功启动即证明 validate 通过（任何映射与 schema 不一致都会抛
 * {@code SchemaManagementException} 致启动失败）；此处显式断言全部 5 个实体均被托管，作为整体一致性证据。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class SchemaValidationTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("10.1 ddl-auto=validate 下全部 5 个实体映射与 V1 schema 一致：上下文启动无 SchemaManagementException")
    void testAllEntityMappings_consistentWithSchema() {
        Set<String> managedEntities = entityManager.getMetamodel().getEntities().stream()
                .map(e -> e.getJavaType().getSimpleName())
                .collect(Collectors.toSet());

        assertThat(managedEntities).containsExactlyInAnyOrder(
                "Session", "Message", "AgentEvent", "ToolCall", "ModerationAudit");
    }
}
