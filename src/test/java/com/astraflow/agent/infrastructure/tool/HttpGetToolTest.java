package com.astraflow.agent.infrastructure.tool;

import com.astraflow.agent.domain.tool.OutboundRequestInspector;
import com.astraflow.agent.domain.tool.ToolContext;
import com.astraflow.agent.domain.tool.ToolErrorCode;
import com.astraflow.agent.domain.tool.ToolResult;
import com.astraflow.agent.domain.tool.ToolResultStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HttpGetTool} 测试，用 WireMock 起本地 server 测真实 HTTP 行为，不触外网（design D8）。
 *
 * <p>覆盖 spec「内置工具 http_get 及出站审查接缝」全部场景：默认放行 / 拦截 / 网络失败 / HTTP 错误 / 只读标记。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@ExtendWith(MockitoExtension.class)
class HttpGetToolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("默认 no-op 审查放行，WireMock 200 → SUCCESS 携带响应体")
    void testCall_defaultAllowReturnsBody() {
        wireMockServer.stubFor(get(urlEqualTo("/api")).willReturn(ok("hello")));
        HttpGetTool tool = newTool(new NoOpOutboundRequestInspector());

        ToolResult result = tool.call(ctx(wireMockServer.url("/api")));

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.output()).isEqualTo("hello");
    }

    @Test
    @DisplayName("审查拦截 → 不发请求，返回 BLOCKED_BY_OUTBOUND_POLICY")
    void testCall_inspectorBlocksReturnsError() {
        OutboundRequestInspector blocking = mock(OutboundRequestInspector.class);
        when(blocking.inspect(anyString())).thenReturn(com.astraflow.agent.domain.tool.InspectionResult.block("域名不在白名单"));
        HttpGetTool tool = newTool(blocking);

        ToolResult result = tool.call(ctx(wireMockServer.url("/api")));

        assertThat(result.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.BLOCKED_BY_OUTBOUND_POLICY);
        wireMockServer.verify(0, getRequestedFor(urlEqualTo("/api")));
    }

    @Test
    @DisplayName("目标不可达 → NETWORK_ERROR，不抛异常外泄")
    void testCall_networkFailureReturnsError() {
        WireMockServer dead = new WireMockServer(options().dynamicPort());
        dead.start();
        String url = dead.url("/x");
        dead.stop();
        HttpGetTool tool = newTool(new NoOpOutboundRequestInspector());

        ToolResult result = tool.call(ctx(url));

        assertThat(result.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.NETWORK_ERROR);
    }

    @Test
    @DisplayName("HTTP 5xx → HTTP_ERROR 含状态码")
    void testCall_httpErrorReturnsError() {
        wireMockServer.stubFor(get(urlEqualTo("/err")).willReturn(aResponse().withStatus(500)));
        HttpGetTool tool = newTool(new NoOpOutboundRequestInspector());

        ToolResult result = tool.call(ctx(wireMockServer.url("/err")));

        assertThat(result.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.HTTP_ERROR);
        assertThat(result.errorMessage()).contains("500");
    }

    @Test
    @DisplayName("http_get.isReadOnly() 为 false")
    void testIsReadOnly_false() {
        HttpGetTool tool = newTool(new NoOpOutboundRequestInspector());

        assertThat(tool.isReadOnly()).isFalse();
    }

    private HttpGetTool newTool(OutboundRequestInspector inspector) {
        HttpToolProperties properties = new HttpToolProperties();
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        return new HttpGetTool(RestClient.builder(), inspector, properties);
    }

    private ToolContext ctx(String url) {
        ObjectNode input = mapper.createObjectNode();
        input.put("url", url);
        return new ToolContext(null, null, null, input);
    }
}
