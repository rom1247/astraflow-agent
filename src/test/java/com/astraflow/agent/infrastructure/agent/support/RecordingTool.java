package com.astraflow.agent.infrastructure.agent.support;

import com.astraflow.agent.domain.tool.Tool;
import com.astraflow.agent.domain.tool.ToolContext;
import com.astraflow.agent.domain.tool.ToolErrorCode;
import com.astraflow.agent.domain.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试用记录型工具：记录执行线程（是否虚拟）与时间区间，可注入延迟与业务失败，供
 * {@code ParallelExecuteToolsAction} 的并行 / 保序 / 失败隔离断言。
 *
 * @author yangyongli@zjxhedu.com
 * @since 2026/7/17
 */
public class RecordingTool implements Tool {

    private final String name;

    private final boolean fail;

    private final long delayMillis;

    private final List<Record> records = new CopyOnWriteArrayList<>();

    /**
     * 构造记录工具。
     *
     * @param name         工具名
     * @param fail         是否业务失败
     * @param delayMillis  执行延迟（用于制造并行时间窗口重叠）
     */
    public RecordingTool(String name, boolean fail, long delayMillis) {
        this.name = name;
        this.fail = fail;
        this.delayMillis = delayMillis;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return "测试记录工具: " + name;
    }

    @Override
    public JsonNode inputSchema() {
        return JsonNodeFactory.instance.objectNode().put("type", "object");
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolResult call(ToolContext context) {
        boolean virtual = Thread.currentThread().isVirtual();
        long start = System.nanoTime();
        sleep(delayMillis);
        long end = System.nanoTime();
        records.add(new Record(virtual, start, end));
        if (fail) {
            return ToolResult.error(ToolErrorCode.INVALID_EXPRESSION, "故意失败: " + name);
        }
        return ToolResult.success(name + "_ok");
    }

    /**
     * @return 执行记录（线程虚拟性 + 起止纳秒）
     */
    public List<Record> records() {
        return records;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    /** 单次执行记录。 */
    public record Record(boolean virtual, long startNanos, long endNanos) {
    }
}
