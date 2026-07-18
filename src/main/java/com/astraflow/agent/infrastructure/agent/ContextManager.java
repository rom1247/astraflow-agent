package com.astraflow.agent.infrastructure.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文截断兜底（context-truncation spec，design D6）。
 *
 * <p>当 {@code systemPrompt + 历史消息 + 本轮用户消息} 的估算 token 数超过预算阈值时，保留
 * {@code systemPrompt} 与最近若干轮消息、截断更早的历史，使总估算长度回落到预算内。截断规则：
 * <ul>
 *   <li>{@code systemPrompt} 始终位于首位且不参与截断（spec「system_prompt 始终保留」）</li>
 *   <li>本轮用户消息（末尾）始终保留</li>
 *   <li>截断粒度按 {@code tool_call/tool_result} 配对单元——assistant(toolCalls) 与其后随的
 *       {@link ToolResponseMessage} 视为不可分割单元，同留或同删，避免孤儿（spec「保持配对完整」）</li>
 *   <li>从最早单元起逐个丢弃，直到总估算 ≤ 预算，但永不低于 {@code keepRounds} 个保留单元</li>
 *   <li>剩余消息保持原序（spec「保持原有顺序」）</li>
 * </ul>
 *
 * <p>token 以保守字符数近似计量（MVP 不引入精确 tokenizer，YAGNI）。
 *
 * <p>纯函数（无副作用、无 Spring 依赖），可直接单测；参数经 {@code AgentProperties} 由调用方传入。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public final class ContextManager {

    private ContextManager() {
    }

    /**
     * 截断历史消息使其落入上下文预算。
     *
     * @param messages     历史消息（不含 systemPrompt，末尾为本轮用户消息）
     * @param systemPrompt 系统提示词（可空；非空时置于返回列表首位且不截断）
     * @param budget       上下文 token 预算阈值（systemPrompt + 历史总估算上限）
     * @param keepRounds   截断时保留的最小单元数（兜底下限）
     * @return {@code [SystemMessage?] + 截断后的历史消息}，保持原序
     */
    public static List<Message> truncate(List<Message> messages, String systemPrompt, int budget, int keepRounds) {
        List<Message> result = new ArrayList<>();
        int systemTokens = estimateTokens(systemPrompt);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            result.add(new SystemMessage(systemPrompt));
        }

        List<List<Message>> units = groupIntoUnits(messages);
        int floor = Math.max(1, keepRounds);
        int startUnit = 0;
        // 从最早单元起丢弃，直到总估算 ≤ 预算；但保留单元数不得低于 floor
        while (units.size() - startUnit > floor
                && sumTokens(units, startUnit) + systemTokens > budget) {
            startUnit++;
        }
        for (int i = startUnit; i < units.size(); i++) {
            result.addAll(units.get(i));
        }
        return result;
    }

    /**
     * 把消息序列划分为截断单元：assistant(toolCalls) 与其后随的 {@link ToolResponseMessage} 合为一组，
     * 其余单条消息各自成单元。保证截断时不拆散 tool_call/tool_result 配对。
     *
     * @param messages 原始消息序列
     * @return 单元列表（每个单元含一或多个消息，保持原序）
     */
    private static List<List<Message>> groupIntoUnits(List<Message> messages) {
        List<List<Message>> units = new ArrayList<>();
        int i = 0;
        while (i < messages.size()) {
            Message current = messages.get(i);
            if (current instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                List<Message> unit = new ArrayList<>();
                unit.add(assistant);
                i++;
                while (i < messages.size() && messages.get(i) instanceof ToolResponseMessage) {
                    unit.add(messages.get(i));
                    i++;
                }
                units.add(unit);
            } else {
                units.add(new ArrayList<>(List.of(current)));
                i++;
            }
        }
        return units;
    }

    /** 累加 {@code units[startUnit..]} 中所有消息的 token 估算。 */
    private static int sumTokens(List<List<Message>> units, int startUnit) {
        int sum = 0;
        for (int i = startUnit; i < units.size(); i++) {
            for (Message message : units.get(i)) {
                sum += estimateTokens(message);
            }
        }
        return sum;
    }

    /**
     * 估算文本 token 数（保守字符数近似，spec「token 以估算方式计量」）。
     *
     * @param text 文本（可空）
     * @return 非负整数估算值
     */
    static int estimateTokens(String text) {
        return text == null ? 0 : text.length();
    }

    /**
     * 估算单条消息的 token 数：文本内容长度 + toolCall 入参 + 工具响应数据长度之和。
     *
     * @param message 消息（可空）
     * @return 非负整数估算值
     */
    static int estimateTokens(Message message) {
        if (message == null) {
            return 0;
        }
        int estimate = estimateTokens(message.getText());
        if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
            for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                estimate += estimateTokens(toolCall.arguments());
            }
        }
        if (message instanceof ToolResponseMessage toolResponse) {
            for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
                estimate += estimateTokens(response.responseData());
            }
        }
        return estimate;
    }

    /**
     * 估算消息序列的总 token 数。
     *
     * @param messages 消息序列（可空）
     * @return 非负整数估算值
     */
    static int estimateTokens(List<Message> messages) {
        if (messages == null) {
            return 0;
        }
        int sum = 0;
        for (Message message : messages) {
            sum += estimateTokens(message);
        }
        return sum;
    }
}
