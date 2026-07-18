package com.astraflow.agent.domain.tool;

import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static lombok.AccessLevel.PRIVATE;

/**
 * 工具调用编排器（领域服务）：统一入口编排「查找 → 校验 → 执行」。
 *
 * <p>{@code invoke(name, ctx)} 流程：
 * <ol>
 *   <li>{@link ToolRegistry#find} 未命中 → {@link ToolErrorCode#UNKNOWN_TOOL} 错误</li>
 *   <li>{@link ToolInputValidator#validate} 失败 → {@link ToolErrorCode#SCHEMA_VALIDATION_FAILED} 错误，<b>不触发 call</b></li>
 *   <li>校验通过 → {@link Tool#call}，业务错误原样透传（错误码区分 schema 错误与业务错误）</li>
 * </ol>
 *
 * <p>#4 的虚拟线程 fan-out 直接调 {@code invoke} 即可获得「校验 + 执行」（design D6）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class ToolInvoker {

    ToolRegistry toolRegistry;
    ToolInputValidator toolInputValidator;

    /**
     * 编排查找 → 校验 → 执行。
     *
     * @param name    工具名
     * @param context 工具调用上下文
     * @return 工具执行结果（未知工具 / 校验失败 / 业务结果）
     */
    public ToolResult invoke(String name, ToolContext context) {
        Optional<Tool> tool = toolRegistry.find(name);
        if (tool.isEmpty()) {
            return ToolResult.error(ToolErrorCode.UNKNOWN_TOOL, "未知工具: " + name);
        }
        Tool target = tool.get();
        ValidationResult validation = toolInputValidator.validate(target, context.input());
        if (!validation.valid()) {
            return ToolResult.error(ToolErrorCode.SCHEMA_VALIDATION_FAILED, validation.errorMessage());
        }
        return target.call(context);
    }
}
