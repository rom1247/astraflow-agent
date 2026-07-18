package com.astraflow.agent.domain.tool;

/**
 * 出站请求审查端口（领域端口）。
 *
 * <p>http_get 在发送请求<b>之前</b>咨询本端口，由其决定放行或拦截。默认提供放行所有请求的 no-op 实现；
 * #7（moderation）在此端口挂载域名白名单判定逻辑，http_get 自身不含白名单判定（spec「出站审查接缝」）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public interface OutboundRequestInspector {

    /**
     * 审查出站请求目标 URL。
     *
     * @param url 目标 URL
     * @return 放行 / 拦截结果（拦截时携带原因）
     */
    InspectionResult inspect(String url);
}
