package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.model.Decision;
import com.astraflow.agent.domain.model.ModerationBlockedException;
import com.astraflow.agent.domain.port.ModerationService;
import org.bsc.langgraph4j.hook.EdgeHook;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.util.Optional;

/**
 * 图内审核挂载点装配（moderation-hook spec「工具执行节点审核挂载点」，design D7）。
 *
 * <p>以 {@link EdgeHook.WrapCall} 包裹工具执行节点（executeToolsHook 槽位）：
 * <ul>
 *   <li>③ 工具执行前：审核工具入参（{@code AssistantMessage.toolCalls.arguments}），
 *       若 {@link ModerationService} 返回 {@link Decision#BLOCK} 则返回失败 future
 *       （携带 {@link ModerationBlockedException}），不执行工具——由 {@code AgentEngine} 映射为
 *       {@code DONE(error_blocked_tool)}</li>
 *   <li>④ 工具执行后：审核工具结果（脱敏细则属 #7，MVP 默认 NoOp 全 PASS 不介入）</li>
 * </ul>
 *
 * <p>{@code callModelHook}（审核 ① ②）在本 change 为图外（#5/#7）职责，{@link AgentGraphFactory} 对该槽位
 * 传 {@code null}（不装配）；最大轮次由原生递归限制承载、非 callModelHook 计数（见 design D8）。{@link ModerationService} 默认 {@code NoOpModerationService} 全 PASS，
 * MVP 不触发任何 BLOCK。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public class ModerationHooks {

    /** 工具入参审核挂载点标识。 */
    private static final String TOOL_NODE = "tool";

    private final ModerationService moderationService;

    /**
     * @param moderationService 审核端口（生产默认 NoOpModerationService）
     */
    public ModerationHooks(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /**
     * 提供包裹工具执行节点的 executeToolsHook。
     *
     * @return executeToolsHook
     */
    public EdgeHook.WrapCall<AgentExecutor.State> executeToolsHook() {
        return (sourceId, state, config, action) -> {
            Optional<Message> lastMessage = state.lastMessage();
            if (lastMessage.isPresent() && lastMessage.get() instanceof AssistantMessage assistant
                    && assistant.hasToolCalls()) {
                for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                    if (moderationService.moderate(toolCall.arguments(), TOOL_NODE) == Decision.BLOCK) {
                        // 同步抛出以中止图（与 MaxTurnsGuard 一致：failedFuture 不传播为流错误，同步异常传播）
                        throw new ModerationBlockedException("工具入参被审核拦截: " + toolCall.name());
                    }
                }
            }
            // ④ 工具执行后审核结果：默认 NoOp 全 PASS 透传（脱敏细则属 #7）
            return action.apply(state, config);
        };
    }
}
