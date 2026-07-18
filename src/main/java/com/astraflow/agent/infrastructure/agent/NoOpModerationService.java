package com.astraflow.agent.infrastructure.agent;

import com.astraflow.agent.domain.model.Decision;
import com.astraflow.agent.domain.port.ModerationService;
import org.springframework.stereotype.Component;

/**
 * {@link ModerationService} 默认实现：对所有内容返回 {@link Decision#PASS}（moderation-hook spec「默认实现全部放行」）。
 *
 * <p>MVP 不实现任何审核规则；#7 届时提供 {@code @Primary} 的关键词 / 正则 / 白名单实现替换本 no-op。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@Component
public class NoOpModerationService implements ModerationService {

    @Override
    public Decision moderate(String content, String node) {
        return Decision.PASS;
    }
}
