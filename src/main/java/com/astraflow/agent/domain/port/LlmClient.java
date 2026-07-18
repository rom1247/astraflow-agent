package com.astraflow.agent.domain.port;

import com.astraflow.agent.domain.model.LlmChunk;
import com.astraflow.agent.domain.model.LlmRequest;
import reactor.core.publisher.Flux;

/**
 * LLM 调用领域端口（零 Spring AI 依赖，design D1/D7）。
 *
 * <p>领域服务 / 应用层（Phase 1 摘要、截断等）与单元测试 mock 经此端口调用 LLM；
 * 实现类 {@code SpringAiLlmClient}（infra）委托经韧性装饰的 {@code ChatModel}。
 * 失败以 {@link com.astraflow.agent.domain.model.LlmException} 终止流。
 *
 * <p><b>边界</b>：图路径（{@code infrastructure/agent}）直接注入 {@code ChatModel}，不经本端口
 * （design D1）；本端口约束 domain/application 层，二者共享同一装饰底层。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public interface LlmClient {

    /**
     * 流式调用 LLM，返回按序到达的 token 增量流。
     *
     * @param request 调用请求
     * @return token 增量流；失败以 {@link com.astraflow.agent.domain.model.LlmException} error 信号终止
     */
    Flux<LlmChunk> stream(LlmRequest request);
}
