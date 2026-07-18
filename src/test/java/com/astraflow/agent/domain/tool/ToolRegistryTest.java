package com.astraflow.agent.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ToolRegistry} 注册 / 查找 / 清单测试。
 *
 * <p>覆盖 spec「工具注册与查找」全部场景：注册后查找成功、查找不存在返回空、重复注册被拒、清单可序列化。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ToolRegistryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("注册后按名查找成功")
    void testFind_registeredReturnsPresent() {
        Tool calculator = stub("calculator");
        ToolRegistry registry = new ToolRegistry(List.of(calculator));

        Optional<Tool> found = registry.find("calculator");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow()).isSameAs(calculator);
    }

    @Test
    @DisplayName("查找不存在的工具返回空 Optional，不抛异常")
    void testFind_unknownReturnsEmpty() {
        ToolRegistry registry = new ToolRegistry(List.of(stub("calculator")));

        assertThat(registry.find("unknown_tool")).isEmpty();
    }

    @Test
    @DisplayName("重复注册同名工具被拒绝，抛业务异常含工具名")
    void testConstructor_duplicateNameThrows() {
        Tool first = stub("calculator");
        Tool second = stub("calculator");

        assertThatThrownBy(() -> new ToolRegistry(List.of(first, second)))
                .isInstanceOf(ToolRegistrationException.class)
                .hasMessageContaining("calculator");
    }

    @Test
    @DisplayName("describeAll 返回含 name+description+inputSchema 的清单且可 Jackson 序列化")
    void testDescribeAll_returnsSerializableDescriptors() throws Exception {
        Tool calculator = stub("calculator");
        Tool httpGet = stub("http_get");
        ToolRegistry registry = new ToolRegistry(List.of(calculator, httpGet));

        List<ToolDescriptor> descriptors = registry.describeAll();

        assertThat(descriptors).hasSize(2);
        assertThat(descriptors).extracting(ToolDescriptor::name)
                .containsExactly("calculator", "http_get");

        String json = mapper.writeValueAsString(descriptors);
        assertThat(json).contains("\"name\":\"calculator\"");
        assertThat(json).contains("\"name\":\"http_get\"");
    }

    private Tool stub(String name) {
        Tool tool = mock(Tool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(name + " 工具");
        when(tool.inputSchema()).thenReturn(schemaNode());
        return tool;
    }

    private JsonNode schemaNode() {
        return mapper.valueToTree(Map.of("type", "object"));
    }
}
