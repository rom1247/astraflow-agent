package com.astraflow.agent.infrastructure.tool;

import com.astraflow.agent.domain.tool.InspectionResult;
import com.astraflow.agent.domain.tool.OutboundRequestInspector;
import org.springframework.stereotype.Component;

/**
 * 出站审查默认 no-op 实现：放行所有请求。
 *
 * <p>#7（moderation）提供域名白名单实现替换本默认 bean。design D2：默认放行实现下沉 infrastructure。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Component
public class NoOpOutboundRequestInspector implements OutboundRequestInspector {

    @Override
    public InspectionResult inspect(String url) {
        return InspectionResult.allow();
    }
}
