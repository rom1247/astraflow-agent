package com.astraflow.agent.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolInputValidator} 测试。
 *
 * <p>覆盖 spec「工具入参 JSON Schema 校验」全部场景：合法通过、缺必填、类型错误、字段超长、空 / null 边界。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class ToolInputValidatorTest {

    private final ToolInputValidator validator = new ToolInputValidator();

    private final ObjectMapper mapper = new ObjectMapper();

    /** 4.1 合法入参（含必填 expression）通过校验。 */
    @Test
    @DisplayName("合法入参（含必填 expression）通过校验")
    void testValidate_validInputPasses() throws Exception {
        Tool tool = toolWith(expressionSchema());

        ValidationResult result = validator.validate(tool, mapper.readTree("{\"expression\":\"1+2\"}"));

        assertThat(result.valid()).isTrue();
    }

    /** 4.2 缺必填字段 expression 校验失败，错误含缺失字段名。 */
    @Test
    @DisplayName("缺必填字段 expression 校验失败，错误含字段名")
    void testValidate_missingRequiredFailsWithFieldName() throws Exception {
        Tool tool = toolWith(expressionSchema());

        ValidationResult result = validator.validate(tool, mapper.readTree("{}"));

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).contains("expression");
    }

    /** 4.3 字段类型错误（a 应为 number 却给字符串）校验失败，错误含类型信息。 */
    @Test
    @DisplayName("字段类型错误（a 应为 number 却给字符串）校验失败，错误含类型信息")
    void testValidate_wrongTypeFails() throws Exception {
        Tool tool = toolWith(numberSchema());

        ValidationResult result = validator.validate(tool, mapper.readTree("{\"a\":\"x\"}"));

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).containsAnyOf("类型", "type", "number");
    }

    /** 4.4 超长 expression（超过 maxLength）校验失败。 */
    @Test
    @DisplayName("超长 expression（超过 maxLength）校验失败")
    void testValidate_tooLongFails() throws Exception {
        Tool tool = toolWith(expressionSchema());

        // maxLength=10，给 11 字符
        ValidationResult result = validator.validate(tool, mapper.readTree("{\"expression\":\"12345678901\"}"));

        assertThat(result.valid()).isFalse();
    }

    /** 4.5 空 JSON（{}）或 null 入参判定非法。 */
    @Test
    @DisplayName("空 JSON 或 null 入参判定非法")
    void testValidate_emptyOrNullInvalid() throws Exception {
        Tool tool = toolWith(expressionSchema());

        // null 前置拦截
        assertThat(validator.validate(tool, null).valid()).isFalse();
        // {} 缺必填亦非法
        assertThat(validator.validate(tool, mapper.readTree("{}")).valid()).isFalse();
    }

    private JsonNode expressionSchema() throws Exception {
        return mapper.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "expression": {"type": "string", "maxLength": 10}
                  },
                  "required": ["expression"]
                }
                """);
    }

    private JsonNode numberSchema() throws Exception {
        return mapper.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "a": {"type": "number"}
                  }
                }
                """);
    }

    private Tool toolWith(JsonNode schema) {
        return new ToolWithSchema(schema);
    }

    /** 测试桩工具：inputSchema 由构造传入，供校验器读取。 */
    static final class ToolWithSchema implements Tool {
        private final JsonNode schema;

        ToolWithSchema(JsonNode schema) {
            this.schema = schema;
        }

        @Override
        public String name() {
            return "test_tool";
        }

        @Override
        public String description() {
            return "测试工具";
        }

        @Override
        public JsonNode inputSchema() {
            return schema;
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }

        @Override
        public ToolResult call(ToolContext context) {
            return ToolResult.success("ok");
        }
    }
}
