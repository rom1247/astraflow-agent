package com.astraflow.agent.domain.tool;

/**
 * 出站审查结果（不可变值对象）。
 *
 * <p>{@code allowed=true} 放行；{@code allowed=false} 拦截，{@code reason} 描述拦截原因（如域名不在白名单）。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/16
 */
public record InspectionResult(boolean allowed, String reason) {

    /**
     * 构造放行结果。
     *
     * @return allowed 为 true 的结果
     */
    public static InspectionResult allow() {
        return new InspectionResult(true, null);
    }

    /**
     * 构造拦截结果。
     *
     * @param reason 拦截原因
     * @return allowed 为 false 的结果
     */
    public static InspectionResult block(String reason) {
        return new InspectionResult(false, reason);
    }
}
