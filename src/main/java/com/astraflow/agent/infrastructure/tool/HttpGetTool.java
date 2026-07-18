package com.astraflow.agent.infrastructure.tool;

import com.astraflow.agent.domain.tool.InspectionResult;
import com.astraflow.agent.domain.tool.OutboundRequestInspector;
import com.astraflow.agent.domain.tool.Tool;
import com.astraflow.agent.domain.tool.ToolContext;
import com.astraflow.agent.domain.tool.ToolErrorCode;
import com.astraflow.agent.domain.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 内置工具 http_get：对外发 GET 请求（{@code isReadOnly=false}，有副作用）。
 *
 * <p>发请求<b>前</b>咨询 {@link OutboundRequestInspector}（默认放行，#7 挂域名白名单）：
 * <ul>
 *   <li>拦截 → {@link ToolErrorCode#BLOCKED_BY_OUTBOUND_POLICY}，不发请求</li>
 *   <li>成功 → {@link ToolResultStatus#SUCCESS} 携带响应体</li>
 *   <li>网络失败 → {@link ToolErrorCode#NETWORK_ERROR}，HTTP 错误状态 → {@link ToolErrorCode#HTTP_ERROR}，
 *       均不抛异常外泄（spec「http_get 及出站审查接缝」）</li>
 * </ul>
 *
 * <p>design D2/D4：依赖 HTTP 客户端（IO）下沉 infrastructure，用 RestClient（{@code spring-boot-starter-restclient}），
 * 超时经 {@link HttpToolProperties} 配置。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Component
public class HttpGetTool implements Tool {

    /** 工具名（全小写下划线）。 */
    private static final String NAME = "http_get";

    /** 入参 JSON Schema。 */
    private static final JsonNode SCHEMA = buildSchema();

    private final RestClient restClient;
    private final OutboundRequestInspector inspector;

    /**
     * 构造 http_get 工具。
     *
     * @param restClientBuilder RestClient 构建器（由 starter 自动装配）
     * @param inspector         出站审查端口
     * @param properties        超时配置
     */
    public HttpGetTool(RestClient.Builder restClientBuilder, OutboundRequestInspector inspector, HttpToolProperties properties) {
        this.inspector = inspector;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.restClient = restClientBuilder.requestFactory(factory).build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "对指定 URL 发起 HTTP GET 请求，返回响应体。";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ToolResult call(ToolContext context) {
        String url = context.input() != null ? context.input().get("url").asText() : null;
        InspectionResult inspection = inspector.inspect(url);
        if (!inspection.allowed()) {
            return ToolResult.error(ToolErrorCode.BLOCKED_BY_OUTBOUND_POLICY, "被出站策略拦截: " + inspection.reason());
        }
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            return ToolResult.success(body);
        } catch (RestClientResponseException httpError) {
            return ToolResult.error(ToolErrorCode.HTTP_ERROR, "HTTP 错误: " + httpError.getStatusCode().value());
        } catch (ResourceAccessException networkError) {
            return ToolResult.error(ToolErrorCode.NETWORK_ERROR, "网络失败: " + rootMessage(networkError));
        } catch (RestClientException restError) {
            return ToolResult.error(ToolErrorCode.NETWORK_ERROR, "请求失败: " + rootMessage(restError));
        }
    }

    private static String rootMessage(Exception exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private static JsonNode buildSchema() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "object");
        ObjectNode url = node.putObject("properties").putObject("url");
        url.put("type", "string");
        url.put("format", "uri");
        node.putArray("required").add("url");
        return node;
    }
}
