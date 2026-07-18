package com.astraflow.agent.infrastructure.tool;

import com.astraflow.agent.domain.tool.Tool;
import com.astraflow.agent.domain.tool.ToolContext;
import com.astraflow.agent.domain.tool.ToolErrorCode;
import com.astraflow.agent.domain.tool.ToolInvoker;
import com.astraflow.agent.domain.tool.ToolResult;
import com.astraflow.agent.domain.tool.builtin.CalculatorTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolCallbackAdapter} 单元测试（tool-invocation spec「声明清单来源 + 执行经 ToolInvoker」）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
@ExtendWith(MockitoExtension.class)
class ToolCallbackAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    Tool tool;

    @Mock
    ToolInvoker toolInvoker;

    @Test
    @DisplayName("声明清单 - name/description/inputSchema 与领域工具一致")
    void testGetToolDefinition_matchesDomainTool() throws Exception {
        CalculatorTool calculator = new CalculatorTool();
        ToolCallbackAdapter adapter = new ToolCallbackAdapter(calculator, toolInvoker);

        ToolDefinition definition = adapter.getToolDefinition();

        assertEquals("calculator", definition.name());
        assertEquals(calculator.description(), definition.description());
        assertEquals(mapper.writeValueAsString(calculator.inputSchema()), definition.inputSchema());
    }

    @Test
    @DisplayName("执行 - 经 ToolInvoker 校验失败返回结构化 error，Tool.call 未被调")
    void testCall_whenSchemaFails_returnsStructuredErrorWithoutCallingTool() throws Exception {
        when(tool.name()).thenReturn("calculator");
        when(toolInvoker.invoke(eq("calculator"), any(ToolContext.class)))
                .thenReturn(ToolResult.error(ToolErrorCode.SCHEMA_VALIDATION_FAILED, "缺少必填字段: expression"));

        ToolCallbackAdapter adapter = new ToolCallbackAdapter(tool, toolInvoker);
        String response = adapter.call("{\"wrong\":1}");

        JsonNode node = mapper.readTree(response);
        assertEquals("ERROR", node.get("status").asText());
        assertEquals(ToolErrorCode.SCHEMA_VALIDATION_FAILED.getValue(), node.get("errorCode").asText());
        verify(toolInvoker).invoke(eq("calculator"), any(ToolContext.class));
        verify(tool, never()).call(any(ToolContext.class));
    }

    @Test
    @DisplayName("执行 - 合法入参经 ToolInvoker 成功后序列化响应")
    void testCall_whenValidInput_returnsSerializedSuccess() throws Exception {
        when(tool.name()).thenReturn("calculator");
        when(toolInvoker.invoke(eq("calculator"), any(ToolContext.class)))
                .thenReturn(ToolResult.success(3.0));

        ToolCallbackAdapter adapter = new ToolCallbackAdapter(tool, toolInvoker);
        String response = adapter.call("{\"expression\":\"1+2\"}");

        JsonNode node = mapper.readTree(response);
        assertEquals("SUCCESS", node.get("status").asText());
        assertTrue(node.has("output"), "响应含 output 字段");
        verify(toolInvoker).invoke(eq("calculator"), any(ToolContext.class));
    }
}
