package com.astraflow.agent.domain.tool;

/**
 * 工具入参校验结果（不可变值对象）。
 *
 * <p>{@link ToolInputValidator#validate} 的返回类型：{@code valid=true} 表示入参符合工具 {@code inputSchema()}，
 * 可执行 {@code call}；{@code valid=false} 时 {@code errorMessage} 含可定位的中文描述（缺字段名 / 类型 / 超长等）。
 *
 * <p>工厂命名 {@code pass()/fail()}（而非 {@code valid()/invalid()}）以避开 record 组件 {@code valid} 的访问器同名冲突。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public record ValidationResult(boolean valid, String errorMessage) {

    /**
     * 构造校验通过结果。
     *
     * @return valid 为 true 的结果
     */
    public static ValidationResult pass() {
        return new ValidationResult(true, null);
    }

    /**
     * 构造校验失败结果。
     *
     * @param errorMessage 中文错误描述（含定位信息）
     * @return valid 为 false 的结果
     */
    public static ValidationResult fail(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }
}
