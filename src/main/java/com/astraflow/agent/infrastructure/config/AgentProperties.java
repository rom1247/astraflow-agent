package com.astraflow.agent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AgentLoop 运行时参数（最大轮次 / 上下文预算 / 保留轮数），供 {@code AgentEngine} 与 {@code ContextManager} 读取。
 *
 * <p>前缀 {@code agent}。禁止硬编码（red-lines 魔法值规范），所有运行时阈值外化至此。
 *
 * <pre>
 * agent:
 *   max-turns: 10
 *   context-budget-tokens: 8000
 *   keep-rounds: 4
 * </pre>
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** 单次 run 内 ReAct 循环（agent 节点 → 工具执行节点）的最大轮次。 */
    private int maxTurns = 10;

    /** 上下文 token 预算阈值（保守字符数近似计量，超阈值截断早期历史）。 */
    private int contextBudgetTokens = 8000;

    /** 超预算时保留的最近消息轮数（兜底下限）。 */
    private int keepRounds = 4;

    public int getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    public int getContextBudgetTokens() {
        return contextBudgetTokens;
    }

    public void setContextBudgetTokens(int contextBudgetTokens) {
        this.contextBudgetTokens = contextBudgetTokens;
    }

    public int getKeepRounds() {
        return keepRounds;
    }

    public void setKeepRounds(int keepRounds) {
        this.keepRounds = keepRounds;
    }
}
