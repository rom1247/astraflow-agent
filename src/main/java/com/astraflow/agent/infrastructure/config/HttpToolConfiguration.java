package com.astraflow.agent.infrastructure.config;

import com.astraflow.agent.infrastructure.tool.HttpToolProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * http_get 工具配置：注册 {@link HttpToolProperties} 为配置属性 bean。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Configuration
@EnableConfigurationProperties(HttpToolProperties.class)
public class HttpToolConfiguration {
}
