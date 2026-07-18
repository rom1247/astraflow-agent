package com.astraflow.agent.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Tool} 端口契约测试。
 *
 * <p>用一个测试桩 {@link FakeTool} 实现端口，断言四元组 {@code name()/description()/inputSchema()/isReadOnly()}
 * 非空，且 {@code inputSchema()} 为合法 JSON Schema（networknt 可编译），覆盖 spec「工具暴露完整元数据」。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class FakeToolTest {

    @Test
    @DisplayName("Tool 暴露完整元数据四元组，inputSchema 为合法 JSON Schema")
    void testTool_exposesCompleteMetadataAndValidSchema() {
        Tool fake = new FakeTool();

        assertThat(fake.name()).isEqualTo("fake_tool");
        assertThat(fake.description()).isNotBlank();
        assertThat(fake.isReadOnly()).isTrue();

        JsonNode schema = fake.inputSchema();
        assertThat(schema).isNotNull();
        assertThat(schema.isObject()).isTrue();
        // inputSchema 是合法 JSON Schema：networknt 能编译为 JsonSchema 不抛异常
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonSchema compiled = factory.getSchema(schema);
        assertThat(compiled).isNotNull();
    }

    /** 测试桩工具：实现 {@link Tool} 端口，供本类验证端口契约。 */
    static final class FakeTool implements Tool {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private static final JsonNode SCHEMA;

        static {
            try {
                SCHEMA = MAPPER.readTree("""
                        {
                          "type": "object",
                          "properties": {
                            "expression": {"type": "string"}
                          },
                          "required": ["expression"]
                        }
                        """);
            } catch (IOException e) {
                throw new IllegalStateException("测试桩 inputSchema 构造失败", e);
            }
        }

        @Override
        public String name() {
            return "fake_tool";
        }

        @Override
        public String description() {
            return "测试桩工具";
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
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
