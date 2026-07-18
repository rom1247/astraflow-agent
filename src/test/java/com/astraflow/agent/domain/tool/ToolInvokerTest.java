package com.astraflow.agent.domain.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ToolInvoker} 编排测试。
 *
 * <p>覆盖 spec「校验失败不触发 call」「错误码区分 schema 错误与业务错误」等场景。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@ExtendWith(MockitoExtension.class)
class ToolInvokerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    ToolRegistry toolRegistry;

    @Mock
    ToolInputValidator toolInputValidator;

    @InjectMocks
    ToolInvoker toolInvoker;

    @Test
    @DisplayName("合法入参经 invoke 触发工具 call 返回 SUCCESS")
    void testInvoke_validInputCallsToolAndReturnsSuccess() throws Exception {
        Tool tool = mock(Tool.class);
        ToolContext context = context("{\"expression\":\"1+2\"}");
        when(toolRegistry.find("calculator")).thenReturn(Optional.of(tool));
        when(toolInputValidator.validate(tool, context.input())).thenReturn(ValidationResult.pass());
        when(tool.call(context)).thenReturn(ToolResult.success(3));

        ToolResult result = toolInvoker.invoke("calculator", context);

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.output()).isEqualTo(3);
        verify(tool).call(context);
    }

    @Test
    @DisplayName("缺必填字段经 invoke 返回 SCHEMA_VALIDATION_FAILED 且 call 从未被调用")
    void testInvoke_schemaErrorSkipsCall() throws Exception {
        Tool tool = mock(Tool.class);
        ToolContext context = context("{}");
        when(toolRegistry.find("calculator")).thenReturn(Optional.of(tool));
        when(toolInputValidator.validate(tool, context.input()))
                .thenReturn(ValidationResult.fail("缺少必填字段: expression"));

        ToolResult result = toolInvoker.invoke("calculator", context);

        assertThat(result.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.SCHEMA_VALIDATION_FAILED);
        verify(tool, never()).call(any());
    }

    @Test
    @DisplayName("校验通过但工具业务出错返回业务错误码，可区分 schema 错误")
    void testInvoke_toolBusinessErrorDistinctFromSchemaError() throws Exception {
        Tool tool = mock(Tool.class);
        ToolContext context = context("{\"expression\":\"1/0\"}");
        when(toolRegistry.find("calculator")).thenReturn(Optional.of(tool));
        when(toolInputValidator.validate(tool, context.input())).thenReturn(ValidationResult.pass());
        when(tool.call(context)).thenReturn(ToolResult.error(ToolErrorCode.DIVISION_BY_ZERO, "除零错误"));

        ToolResult result = toolInvoker.invoke("calculator", context);

        assertThat(result.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.DIVISION_BY_ZERO);
        assertThat(result.errorCode()).isNotEqualTo(ToolErrorCode.SCHEMA_VALIDATION_FAILED);
    }

    @Test
    @DisplayName("未知工具返回 UNKNOWN_TOOL 错误")
    void testInvoke_unknownToolReturnsError() throws Exception {
        when(toolRegistry.find("ghost")).thenReturn(Optional.empty());

        ToolResult result = toolInvoker.invoke("ghost", context("{}"));

        assertThat(result.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.UNKNOWN_TOOL);
    }

    private ToolContext context(String inputJson) throws Exception {
        JsonNode input = mapper.readTree(inputJson);
        return new ToolContext(UUID.randomUUID(), "turn-1", "tooluse-1", input);
    }
}
