package com.astraflow.agent.domain.model;

/**
 * 内容审核决策枚举（moderation-hook spec「Decision 枚举」），消除审核结果魔法字符串。
 *
 * <p>{@link com.astraflow.agent.domain.port.ModerationService#moderate} 的返回类型：
 * <ul>
 *   <li>{@link #PASS}：放行</li>
 *   <li>{@link #BLOCK}：拦截（工具入参 BLOCK → {@code DONE(error_blocked_tool)}）</li>
 *   <li>{@link #REDACT}：脱敏后继续（脱敏细则属 #7）</li>
 *   <li>{@link #HUMAN_REVIEW}：转人工（Phase 1）</li>
 * </ul>
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public enum Decision {

    /** 放行。 */
    PASS,

    /** 拦截。 */
    BLOCK,

    /** 脱敏后继续（#7）。 */
    REDACT,

    /** 转人工复核（Phase 1）。 */
    HUMAN_REVIEW
}
