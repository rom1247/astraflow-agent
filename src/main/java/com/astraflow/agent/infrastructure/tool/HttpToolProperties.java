package com.astraflow.agent.infrastructure.tool;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * http_get 工具可配阈值（design D7：可配阈值用 {@code @ConfigurationProperties}，禁硬编码）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tool.http")
public class HttpToolProperties {

    /** 连接超时。 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** 读超时。 */
    private Duration readTimeout = Duration.ofSeconds(10);
}
