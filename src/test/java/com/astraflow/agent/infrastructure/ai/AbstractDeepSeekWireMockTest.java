package com.astraflow.agent.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * DeepSeek WireMock 集成测试基类（change add-llm-and-resilience §1.3）。
 *
 * <p>起本地 WireMock（动态端口）替代 {@code api.deepseek.com}，桩 {@code /chat/completions} 的 SSE 流，
 * 供 {@code ResilientChatModel} / {@code SpringAiLlmClient} 等集成测试复用。<b>不加载 Spring 上下文、不拉 Docker</b>——
 * 测试手工装配指向 WireMock 的真实 {@link DeepSeekChatModel}（design D2），聚焦 LLM 适配 + 韧性行为，避免数据源耦合。
 *
 * <p>SSE 桩格式（OpenAI/DeepSeek 兼容）：每事件 {@code data: <json>\n\n}，末尾 {@code data: [DONE]}。
 * JSON 用 Jackson 构造以规避中文/特殊字符转义 bug。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public abstract class AbstractDeepSeekWireMockTest {

    /** DeepSeek 聊天补全路径（{@code DeepSeekApi} 默认 completionsPath）。 */
    protected static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    /** 测试用模型名（与 application-test.yml 一致，避免魔法值散落）。 */
    protected static final String TEST_MODEL = "deepseek-v4-flash";

    private final ObjectMapper mapper = new ObjectMapper();

    protected WireMockServer wireMockServer;

    /**
     * 起动态端口 WireMock。
     */
    @BeforeEach
    protected void setUpWireMock() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
    }

    /**
     * 停 WireMock。
     */
    @AfterEach
    protected void tearDownWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    /**
     * WireMock 根地址（喂 {@code DeepSeekApi.baseUrl}）。
     *
     * @return 形如 {@code http://localhost:<port>}
     */
    protected String deepSeekBaseUrl() {
        return wireMockServer.baseUrl();
    }

    /**
     * 构造指向 WireMock 的真实 {@link DeepSeekChatModel}（无响应超时）。
     *
     * <p>D6：内部 {@link RetryTemplate} 置为不重试（{@code maxRetries=0}），由 Resilience4j {@code Retry} 统一接管，
     * 避免双重重试污染测试断言（请求计数）。
     *
     * @return 装配好的 DeepSeek 流式模型
     */
    protected DeepSeekChatModel newDeepSeekChatModel() {
        return newDeepSeekChatModel(null);
    }

    /**
     * 构造指向 WireMock 的真实 {@link DeepSeekChatModel}，可配置 reactor-netty 响应超时（用于超时重试用例）。
     *
     * @param responseTimeout 响应超时（null 表示用默认 WebClient，不设超时）
     * @return 装配好的 DeepSeek 流式模型
     */
    protected DeepSeekChatModel newDeepSeekChatModel(java.time.Duration responseTimeout) {
        DeepSeekApi.Builder apiBuilder = DeepSeekApi.builder()
                .baseUrl(deepSeekBaseUrl())
                .apiKey("dummy");
        if (responseTimeout != null) {
            reactor.netty.http.client.HttpClient httpClient =
                    reactor.netty.http.client.HttpClient.create().responseTimeout(responseTimeout);
            apiBuilder.webClientBuilder(
                    org.springframework.web.reactive.function.client.WebClient.builder()
                            .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient)));
        }
        DeepSeekApi api = apiBuilder.build();
        return DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .options(DeepSeekChatOptions.builder().model(TEST_MODEL).build())
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .build();
    }

    /**
     * 构造一个最小用户消息 Prompt（便捷断言用）。
     *
     * @param text 用户消息文本
     * @return Prompt
     */
    protected Prompt userPrompt(String text) {
        return new Prompt(new UserMessage(text));
    }

    // ==================== Resilience4j 快速实例（测试用紧凑时序） ====================

    /** 测试退避间隔（毫秒，远小于生产，避免测试耗时）。 */
    private static final long FAST_BACKOFF_MILLIS = 20L;

    /**
     * 构造测试用快速 Retry：极小退避 + 复用生产 {@link ResilientChatModel#isRetryable} 谓词。
     *
     * @param maxAttempts 最大尝试次数（含首次）
     * @return Retry
     */
    protected Retry fastRetry(int maxAttempts) {
        return Retry.of("test-llm", RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .intervalFunction(IntervalFunction.of(FAST_BACKOFF_MILLIS))
                .retryOnException(ResilientChatModel::isRetryable)
                .build());
    }

    /**
     * 构造测试用 CircuitBreaker。
     *
     * @param config 熔断配置
     * @return CircuitBreaker
     */
    protected CircuitBreaker fastCircuitBreaker(CircuitBreakerConfig config) {
        return CircuitBreaker.of("test-llm", config);
    }

    /**
     * 测试用熔断配置构建器（COUNT_BASED + 半开许可 1，便于断言自愈）。
     *
     * @return CircuitBreakerConfig.Builder
     */
    protected static CircuitBreakerConfig.Builder fastCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .permittedNumberOfCallsInHalfOpenState(1);
    }

    // ==================== SSE 桩辅助 ====================

    /**
     * 桩一次正常的 token 流式响应。
     *
     * @param tokens 按序到达的文本增量（如 "你好"、"，世界"）
     */
    protected void stubStreamTokens(String... tokens) {
        wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(streamResponse(tokens)));
    }

    /**
     * 构造流式响应定义（Content-Type=text/event-stream）。
     *
     * @param tokens 文本增量序列
     * @return WireMock 响应定义构建器
     */
    protected ResponseDefinitionBuilder streamResponse(String... tokens) {
        return aResponse()
                .withHeader("Content-Type", "text/event-stream")
                .withBody(streamingBody(tokens));
    }

    /**
     * 构造错误状态响应定义（429/5xx 等）。
     *
     * @param status         HTTP 状态码
     * @param retryAfterSecs {@code Retry-After} 头秒数（429 用，可为 null）
     * @return WireMock 响应定义构建器
     */
    protected ResponseDefinitionBuilder errorResponse(int status, Integer retryAfterSecs) {
        var response = aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(errorBody(status));
        if (retryAfterSecs != null) {
            response.withHeader("Retry-After", String.valueOf(retryAfterSecs));
        }
        return response;
    }

    /**
     * 构造连接级故障响应（模拟网络中断：连接被重置）。
     *
     * @return WireMock 响应定义构建器（{@code Fault.CONNECTION_RESET_BY_PEER}）
     */
    protected ResponseDefinitionBuilder connectionResetResponse() {
        return aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER);
    }

    /**
     * 桩一次错误状态响应（429/5xx 等）。
     *
     * @param status         HTTP 状态码
     * @param retryAfterSecs {@code Retry-After} 头秒数（429 用，可为 null）
     */
    protected void stubChatCompletionsError(int status, Integer retryAfterSecs) {
        wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(errorResponse(status, retryAfterSecs)));
    }

    /**
     * 桩「首次失败 → 重试时成功」序列（scenario）：第一次返回错误，第二次起返回正常流。
     *
     * @param errorStatus    首次失败的 HTTP 状态码（429/5xx）
     * @param retryAfterSecs {@code Retry-After} 头（可为 null）
     * @param successTokens  恢复后的 token 增量序列
     */
    protected void stubErrorThenStream(int errorStatus, Integer retryAfterSecs, String... successTokens) {
        wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(errorResponse(errorStatus, retryAfterSecs))
                .willSetStateTo("recovered"));
        wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(streamResponse(successTokens)));
    }

    /**
     * 桩「首次连接故障 → 重试时成功」序列（scenario）。
     *
     * @param successTokens 恢复后的 token 增量序列
     */
    protected void stubConnectionResetThenStream(String... successTokens) {
        wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(connectionResetResponse())
                .willSetStateTo("recovered"));
        wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(streamResponse(successTokens)));
    }

    /**
     * 桩「前 {@code failureCount} 次失败 → 之后成功」序列（scenario，线性状态）。
     *
     * <p>供熔断自愈测试用：先连续失败打开熔断，过冷却后半开试探命中成功桩。
     *
     * @param failureCount   初始失败次数（每次返回 errorStatus）
     * @param errorStatus    失败 HTTP 状态码
     * @param retryAfterSecs {@code Retry-After}（可为 null）
     * @param successTokens  转为成功后的 token 增量序列
     */
    protected void stubFailuresThenStream(int failureCount, int errorStatus, Integer retryAfterSecs, String... successTokens) {
        String prevState = Scenario.STARTED;
        for (int i = 0; i < failureCount; i++) {
            String nextState = "after-" + i;
            wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                    .inScenario("phase")
                    .whenScenarioStateIs(prevState)
                    .willReturn(errorResponse(errorStatus, retryAfterSecs))
                    .willSetStateTo(nextState));
            prevState = nextState;
        }
        wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                .inScenario("phase")
                .whenScenarioStateIs(prevState)
                .willReturn(streamResponse(successTokens)));
    }

    /**
     * 桩「首次响应延迟（超阈值）→ 重试时及时」序列（scenario）。
     *
     * <p>首次返回错误且固定延迟，超 reactor-netty 响应超时；第二次起返回正常流。
     *
     * @param delayMillis    首次响应延迟（毫秒，应大于模型 responseTimeout）
     * @param successTokens  恢复后的 token 增量序列
     */
    protected void stubDelayedErrorThenStream(int delayMillis, String... successTokens) {
        wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(errorResponse(500, null).withFixedDelay(delayMillis))
                .willSetStateTo("recovered"));
        wireMockServer.stubFor(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(streamResponse(successTokens)));
    }

    /**
     * 构造正常流式响应 SSE 正文：逐 token 增量 + 终止 {@code finish_reason=stop} + {@code [DONE]}。
     *
     * @param tokens 文本增量序列
     * @return SSE 正文
     */
    protected String streamingBody(String... tokens) {
        StringBuilder body = new StringBuilder();
        String id = "chatcmpl-test";
        for (String token : tokens) {
            body.append(sseData(chunkJson(id, token, null)));
        }
        body.append(sseData(chunkJson(id, null, "stop")));
        body.append("data: [DONE]\n\n");
        return body.toString();
    }

    private String sseData(String json) {
        return "data: " + json + "\n\n";
    }

    private String chunkJson(String id, String content, String finishReason) {
        ObjectNode root = mapper.createObjectNode();
        root.put("id", id);
        root.put("object", "chat.completion.chunk");
        root.put("created", 1700000000);
        root.put("model", TEST_MODEL);
        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        ObjectNode delta = choice.putObject("delta");
        if (content != null) {
            delta.put("content", content);
        }
        if (finishReason != null) {
            choice.put("finish_reason", finishReason);
        } else {
            choice.putNull("finish_reason");
        }
        return root.toString();
    }

    private String errorBody(int status) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode error = root.putObject("error");
        error.put("message", "stub error " + status);
        error.put("type", status == 429 ? "rate_limit_exceeded" : "server_error");
        return root.toString();
    }
}
