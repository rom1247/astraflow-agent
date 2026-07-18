package com.astraflow.agent.domain.tool.builtin;

import com.astraflow.agent.domain.tool.ToolContext;
import com.astraflow.agent.domain.tool.ToolErrorCode;
import com.astraflow.agent.domain.tool.ToolResult;
import com.astraflow.agent.domain.tool.ToolResultStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CalculatorTool} 测试。
 *
 * <p>覆盖 spec「内置工具 calculator」全部场景：合法求值、非算术字符、除零、只读标记。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
class CalculatorToolTest {

    private final CalculatorTool calculator = new CalculatorTool();

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("合法算术表达式 (1+2)*3 求值得 9")
    void testCall_validExpressionEvaluates() {
        ToolResult result = calculator.call(ctx("(1+2)*3"));

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.output()).isEqualTo(9.0);
    }

    @Test
    @DisplayName("含非算术字符的非法表达式返回 INVALID_EXPRESSION 且无副作用")
    void testCall_nonArithmeticReturnsError() {
        ToolResult result = calculator.call(ctx("1;System.exit(0)"));

        assertThat(result.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.INVALID_EXPRESSION);
    }

    @Test
    @DisplayName("除零表达式 1/0 返回 DIVISION_BY_ZERO")
    void testCall_divisionByZeroReturnsError() {
        ToolResult result = calculator.call(ctx("1/0"));

        assertThat(result.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(ToolErrorCode.DIVISION_BY_ZERO);
    }

    @Test
    @DisplayName("calculator.isReadOnly() 为 true")
    void testIsReadOnly_true() {
        assertThat(calculator.isReadOnly()).isTrue();
    }

    private ToolContext ctx(String expression) {
        ObjectNode input = mapper.createObjectNode();
        input.put("expression", expression);
        return new ToolContext(null, null, null, input);
    }
}
