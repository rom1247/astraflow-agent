package com.astraflow.agent.domain.tool;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工具注册中心（领域服务，内存态）。
 *
 * <p>构造期由 Spring 收集所有 {@link Tool} bean（注入 {@code List<Tool>}），以 {@link Tool#name()} 为唯一键入表；
 * 遇同名重复注册立即抛 {@link ToolRegistrationException}（禁静默覆盖，保留首个）。提供按名查找与给 LLM 的清单输出。
 *
 * <p>design D5：内存 Map 只有一种实现，不拆接口 / 实现（YAGNI）；遵循 {@code domain-service.md} 规范精神。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Service
public class ToolRegistry {

    private final Map<String, Tool> tools;

    /**
     * 构造注册中心。
     *
     * @param toolList Spring 收集的全部工具 bean
     * @throws ToolRegistrationException 存在同名工具
     */
    public ToolRegistry(List<Tool> toolList) {
        Map<String, Tool> map = new LinkedHashMap<>();
        for (Tool tool : toolList) {
            Tool existing = map.putIfAbsent(tool.name(), tool);
            if (existing != null) {
                throw new ToolRegistrationException("重复注册同名工具: " + tool.name());
            }
        }
        this.tools = Collections.unmodifiableMap(map);
    }

    /**
     * 按名查找工具。
     *
     * @param name 工具名
     * @return 命中返回 {@link Optional#of(Object)}，未注册返回 {@link Optional#empty()}（禁 null）
     */
    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 输出给 LLM 的工具清单（每项含 name + description + inputSchema，保持注册顺序，可 Jackson 序列化）。
     *
     * @return 工具描述清单
     */
    public List<ToolDescriptor> describeAll() {
        return tools.values().stream()
                .map(tool -> new ToolDescriptor(tool.name(), tool.description(), tool.inputSchema()))
                .toList();
    }
}
