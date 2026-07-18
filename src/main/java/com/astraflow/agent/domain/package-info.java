/**
 * 领域层：纯领域模型、仓储端口、领域服务、端口接口。零框架依赖（持久化经 JPA 端口，非实现）。
 * <p>
 * 子包：
 * <ul>
 *   <li>model（实体/值对象/枚举）</li>
 *   <li>repository（仓储端口）</li>
 *   <li>service（领域服务）</li>
 *   <li>port（LlmClient/ModerationService 等横切单端口）</li>
 *   <li>tool（工具聚合：Tool 端口 / 值对象 / 注册中心 / 入参校验 / 出站审查端口 + 内置纯计算工具）</li>
 * </ul>
 * <p>
 * 注：{@code tool} 为工具上下文的聚合子包（有意扩展，非 port 归属），IO 型工具实现下沉 infrastructure/tool/。
 * <p>
 * 层访问规则与选型见 CLAUDE.md、docs/tech-selection.md §5。
 */
package com.astraflow.agent.domain;
