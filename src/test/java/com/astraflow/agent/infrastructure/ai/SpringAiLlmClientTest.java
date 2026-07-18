package com.astraflow.agent.infrastructure.ai;

import com.astraflow.agent.domain.model.LlmChunk;
import com.astraflow.agent.domain.model.LlmRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link SpringAiLlmClient} 单元测试（Mockito mock ChatModel，不加载 Spring 上下文 / WireMock）。
 *
 * <p>验证：领域请求适配为 Prompt、ChatResponse 映射为 {@link LlmChunk}、按序返回 token 增量流。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@ExtendWith(MockitoExtension.class)
class SpringAiLlmClientTest {

    @Mock
    private ChatModel chatModel;

    private SpringAiLlmClient client;

    @BeforeEach
    void setUp() {
        client = new SpringAiLlmClient(chatModel);
    }

    @Test
    @DisplayName("正常调用 → 委托装饰后 ChatModel，返回按序 LlmChunk 流")
    void testStream_delegatesAndMapsToChunkFlux() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(
                Flux.just(textResponse("你好"), textResponse("，世界")));

        List<LlmChunk> chunks = client.stream(new LlmRequest("hi")).collectList().block();

        assertThat(chunks).extracting(LlmChunk::text).containsExactly("你好", "，世界");
    }

    private ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
