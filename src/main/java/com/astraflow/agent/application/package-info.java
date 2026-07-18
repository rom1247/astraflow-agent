/**
 * 应用层：应用服务（@Transactional + DTO↔domain）。只能注入 domain Service。
 * <p>
 * 例：SessionAppService / MessageAppService(SSE 编排) / WorkflowAppService。
 */
package com.astraflow.agent.application;
