package com.astraflow.agent.domain.port;

import com.astraflow.agent.domain.model.Decision;

/**
 * 内容审核端口（框架无关的领域端口，moderation-hook spec）。
 *
 * <p>对给定内容返回 {@link Decision}。本 change 仅定义端口与默认全 {@link Decision#PASS} 实现
 * （{@code NoOpModerationService}）；具体的关键词 / 正则 / 出站域名白名单判定属后续 moderation-and-auth
 * change（#7）（端口在本 change 定义、实现在 #7 填充）。
 *
 * <p>零框架依赖，供 {@code infrastructure/agent/} 的图内 hook 挂载点调用。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public interface ModerationService {

    /**
     * 审核给定内容。
     *
     * @param content 待审内容（模型文本 / 工具入参 JSON / 工具结果等）
     * @param node    审核挂载点标识（如 {@code agent} / {@code tool} / {@code output}）
     * @return 审核决策
     */
    Decision moderate(String content, String node);
}
