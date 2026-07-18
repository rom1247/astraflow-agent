package com.astraflow.agent.domain.tool.builtin;

import com.astraflow.agent.application.constant.ToolConstants;
import com.astraflow.agent.domain.tool.Tool;
import com.astraflow.agent.domain.tool.ToolContext;
import com.astraflow.agent.domain.tool.ToolErrorCode;
import com.astraflow.agent.domain.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 内置工具 calculator：对算术表达式安全求值（{@code isReadOnly=true}，纯计算无副作用）。
 *
 * <p>安全策略（design「calculator 安全求值」）：
 * <ul>
 *   <li>白名单正则：仅允许数字、{@code .}、{@code + - * / ( )} 与空白，禁 {@code eval}、函数调用、字母变量</li>
 *   <li>自建递归下降求值器（不引第三方表达式库，YAGNI），禁用脚本引擎</li>
 * </ul>
 * 非法表达式（含非算术字符 / 括号不匹配 / 操作数缺失）返回 {@link ToolErrorCode#INVALID_EXPRESSION}；
 * 除零返回 {@link ToolErrorCode#DIVISION_BY_ZERO}。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
@Component
public class CalculatorTool implements Tool {

    /** 工具名（全小写下划线）。 */
    private static final String NAME = "calculator";

    /** 白名单：仅数字、点、四则运算符、括号、空白。 */
    private static final Pattern ALLOWED = Pattern.compile("^[0-9.+\\-*/()\\s]+$");

    /** 入参 JSON Schema（maxLength 引用 {@link ToolConstants#MAX_EXPRESSION_LENGTH}）。 */
    private static final JsonNode SCHEMA = buildSchema();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "对算术表达式安全求值，仅支持数字与 + - * / ( ) 四则运算。";
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
        JsonNode exprNode = context.input() != null ? context.input().get("expression") : null;
        String expression = exprNode != null ? exprNode.asText() : "";

        if (!ALLOWED.matcher(expression).matches()) {
            return ToolResult.error(ToolErrorCode.INVALID_EXPRESSION, "表达式非法: 含非算术字符");
        }
        try {
            double result = new ArithmeticEvaluator(expression).parse();
            return ToolResult.success(result);
        } catch (ArithmeticException divisionByZero) {
            return ToolResult.error(ToolErrorCode.DIVISION_BY_ZERO, "除零错误");
        } catch (IllegalArgumentException invalid) {
            return ToolResult.error(ToolErrorCode.INVALID_EXPRESSION, "表达式非法: " + invalid.getMessage());
        }
    }

    private static JsonNode buildSchema() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", "object");
        ObjectNode expression = node.putObject("properties").putObject("expression");
        expression.put("type", "string");
        expression.put("maxLength", ToolConstants.MAX_EXPRESSION_LENGTH);
        node.putArray("required").add("expression");
        return node;
    }

    /**
     * 递归下降算术求值器，支持 {@code + - * / ( )} 与一元正负号。
     *
     * <p>文法：{@code expr = term (('+'|'-') term)*}，{@code term = factor (('*'|'/') factor)*}，
     * {@code factor = number | '(' expr ')' | ('+'|'-') factor}。除零抛 {@link ArithmeticException}，
     * 语法错误（括号不匹配 / 操作数缺失 / 多余字符）抛 {@link IllegalArgumentException}。
     */
    private static final class ArithmeticEvaluator {

        private final String input;
        private int pos;

        ArithmeticEvaluator(String input) {
            this.input = input;
            this.pos = 0;
        }

        double parse() {
            double value = expr();
            skipWhitespace();
            if (pos < input.length()) {
                throw new IllegalArgumentException("多余字符: " + input.charAt(pos));
            }
            return value;
        }

        private double expr() {
            double value = term();
            skipWhitespace();
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == '+') {
                    pos++;
                    value += term();
                } else if (c == '-') {
                    pos++;
                    value -= term();
                } else {
                    break;
                }
                skipWhitespace();
            }
            return value;
        }

        private double term() {
            double value = factor();
            skipWhitespace();
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == '*') {
                    pos++;
                    value *= factor();
                } else if (c == '/') {
                    pos++;
                    double divisor = factor();
                    if (divisor == 0.0) {
                        throw new ArithmeticException("division by zero");
                    }
                    value /= divisor;
                } else {
                    break;
                }
                skipWhitespace();
            }
            return value;
        }

        private double factor() {
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("意外结束: 期望操作数");
            }
            char c = input.charAt(pos);
            if (c == '(') {
                pos++;
                double value = expr();
                skipWhitespace();
                if (pos >= input.length() || input.charAt(pos) != ')') {
                    throw new IllegalArgumentException("括号不匹配");
                }
                pos++;
                return value;
            }
            if (c == '+') {
                pos++;
                return factor();
            }
            if (c == '-') {
                pos++;
                return -factor();
            }
            return number();
        }

        private double number() {
            skipWhitespace();
            int start = pos;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("期望数字");
            }
            return Double.parseDouble(input.substring(start, pos));
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }
    }
}
