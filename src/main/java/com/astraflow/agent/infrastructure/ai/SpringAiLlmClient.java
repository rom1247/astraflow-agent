package com.astraflow.agent.infrastructure.ai;

import com.astraflow.agent.domain.model.LlmChunk;
import com.astraflow.agent.domain.model.LlmRequest;
import com.astraflow.agent.domain.port.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * {@link LlmClient} 的 Spring AI 实现（design D1/D7）。
 *
 * <p>注入经韧性装饰的 {@code @Primary ChatModel}（即 {@link ResilientChatModel}），将领域请求适配为
 * Spring AI {@link Prompt}，把 {@link ChatResponse} 映射为 {@link LlmChunk}。领域层经此端口获得 LLM 能力，
 * 与图路径（直接注入 ChatModel）共享同一装饰底层。
 *
 * <p>记录入参与耗时（coding-conventions 外部调用规范）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiLlmClient implements LlmClient {

    private final ChatModel chatModel;

    @Override
    public Flux<LlmChunk> stream(LlmRequest request) {
        // defer：每次订阅独立计时（Flux 可被多次订阅）
        return Flux.defer(() -> {
            long start = System.nanoTime();
            log.info("LLM 流式调用入参: text={}", request.text());
            return chatModel.stream(new Prompt(new UserMessage(request.text())))
                    .map(SpringAiLlmClient::toChunk)
                    .doOnError(error -> log.error("LLM 流式调用失败", error))
                    .doFinally(signal -> log.info("LLM 流式调用结束: signal={} 耗时={}ms",
                            signal, (System.nanoTime() - start) / 1_000_000));
        });
    }

    private static LlmChunk toChunk(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return new LlmChunk("");
        }
        String text = response.getResult().getOutput().getText();
        return new LlmChunk(text == null ? "" : text);
    }
}
