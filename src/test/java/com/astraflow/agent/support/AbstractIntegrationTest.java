package com.astraflow.agent.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 集成测试基类：通过 Testcontainers 拉起真实 PostgreSQL 容器，
 * 以 {@link ServiceConnection} 自动注入数据源，供 Flyway 迁移与 JPA 实体仓储测试统一复用。
 *
 * <p>启用 {@code test} profile（见 {@code application-test.yml}），覆盖主配置中
 * {@code spring.ai.deepseek.api-key} 占位符无默认值导致上下文启动失败的陷阱。
 *
 * <p><b>采用「单例容器」模式</b>（非 {@code @Testcontainers}+{@code @Container} 按类生命周期）：
 * 静态容器在类加载时由 {@code static} 块启动一次，整个测试 JVM 共享，直到 JVM 退出才停止。
 * 理由：{@code @Testcontainers} 的 {@code AfterAll} 会在每个测试类结束后停止容器，
 * 而 {@code @SpringBootTest} 会缓存上下文（共享数据源指向已停止容器的旧端口），
 * 二者冲突导致「第二个测试类」连接超时。单例模式保证缓存上下文的数据源始终指向同一存活容器。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /** 真实 PostgreSQL 容器（postgres:16），单例，整个测试 JVM 共享。 */
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }
}
