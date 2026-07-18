package com.astraflow.agent.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 工具入参 JSON Schema 校验器（领域服务）。
 *
 * <p>调用工具 {@code call} 前，按工具自身 {@link Tool#inputSchema()} 用 networknt {@code json-schema-validator}
 * （1.5.6 旧 API：{@link JsonSchemaFactory#getInstance(SpecVersion.VersionFlag)} + {@link JsonSchemaFactory#getSchema(JsonNode)}
 * + {@link JsonSchema#validate(JsonNode)}）校验入参。校验与工具业务逻辑解耦（工具 {@code call} 自身只处理合法入参）。
 *
 * <p>错误组装（基于 networknt 1.5.6 {@link ValidationMessage}）：
 * <ul>
 *   <li>{@code required}：缺失字段名取 {@link ValidationMessage#getArguments()} 首元素（path 指向父对象）</li>
 *   <li>{@code type}：位置取 {@link ValidationMessage#getProperty()}，期望类型取 arguments</li>
 *   <li>其它（如 {@code maxLength}）：位置 + {@link ValidationMessage#getMessage()}</li>
 * </ul>
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Service
public class ToolInputValidator {

    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    /** 按工具名缓存编译后的 JsonSchema，避免每次 call 重编译（inputSchema 固定）。线程安全，支持 #4 并行 fan-out。 */
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    /**
     * 校验入参是否符合工具的 inputSchema。
     *
     * @param tool  目标工具（提供 inputSchema）
     * @param input 工具入参（解析后的 JSON）
     * @return 校验通过返回 pass，否则返回含可定位描述的 fail
     */
    public ValidationResult validate(Tool tool, JsonNode input) {
        if (input == null || input.isNull()) {
            return ValidationResult.fail("工具入参为空");
        }
        JsonSchema schema = schemaCache.computeIfAbsent(tool.name(), k -> FACTORY.getSchema(tool.inputSchema()));
        Set<ValidationMessage> errors = schema.validate(input);
        if (errors.isEmpty()) {
            return ValidationResult.pass();
        }
        return ValidationResult.fail(format(errors));
    }

    private String format(Set<ValidationMessage> errors) {
        return errors.stream()
                .map(this::formatOne)
                .collect(Collectors.joining("; "));
    }

    private String formatOne(ValidationMessage message) {
        Object[] args = message.getArguments();
        if ("required".equals(message.getType())) {
            return "缺少必填字段: " + fieldFrom(message, args);
        }
        if ("type".equals(message.getType())) {
            String expected = args == null
                    ? ""
                    : Arrays.stream(args).map(Object::toString).collect(Collectors.joining("/"));
            return "字段类型错误: " + locationOf(message) + "（期望 " + expected + "）";
        }
        return locationOf(message) + ": " + message.getMessage();
    }

    /** required 错误：缺失字段名优先取 arguments 首元素，退而取 property / location。 */
    private String fieldFrom(ValidationMessage message, Object[] args) {
        if (args != null && args.length > 0) {
            return args[0].toString();
        }
        return locationOf(message);
    }

    /** 取可读位置：property 优先，否则 instance location。 */
    private String locationOf(ValidationMessage message) {
        String property = message.getProperty();
        if (property != null && !property.isEmpty()) {
            return property;
        }
        return message.getInstanceLocation() != null ? message.getInstanceLocation().toString() : "";
    }
}
