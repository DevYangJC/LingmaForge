package com.lingmaforge.backend.workbench.ai.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 计划追踪器——移植自 zero-code 的 {@code PlanTracker}，为 agentic 迭代修改提供 DAG 纪律。
 *
 * <p>核心能力：
 * <ul>
 *   <li><b>updatePlan</b>：全量替换当前修改计划，验证依赖关系（deps 必须指向已知 id），
 *       一个 {@code in_progress} 任务的 deps 必须全部 {@code completed}，上限 20 项。</li>
 *   <li><b>Nag 防跑偏</b>：模型每执行一次非 {@code updatePlan} 的工具调用，nag 计数器 +1；
 *       累计 ≥3 次后，在下一次工具结果末尾追加提醒，敦促模型更新计划。</li>
 *   <li><b>结构化进度文本</b>：{@link #generateProgressText(String)} 输出 {@code [x] #1 ... (done/total)}
 *       格式，供注入 LLM 上下文和前端可视化。</li>
 * </ul>
 *
 * <p>当前为纯内存实现（{@link ConcurrentHashMap}），项目 ID + 任务 ID 联合为 key。
 * 后续可替换为 Redis 持久化（zero-code 方案），衔接跨轮记忆。</p>
 *
 * @see PlanItem
 */
@Component
public class PlanTracker {

    private static final Logger log = LoggerFactory.getLogger(PlanTracker.class);

    /** 触发 Nag 提醒的连续非 updatePlan 调用数阈值。 */
    private static final int NAG_THRESHOLD = 3;

    /** 单次计划允许的最大项数。 */
    private static final int MAX_PLAN_ITEMS = 20;

    private final ConcurrentHashMap<String, PlanState> plans = new ConcurrentHashMap<>();

    /**
     * 生成 key：projectId 与 taskId 的联合。
     */
    public static String key(Long projectId, String taskId) {
        return projectId + ":" + taskId;
    }

    /**
     * 全量替换当前计划，返回格式化进度文本。
     *
     * @param key  计划 key
     * @param items 新计划项列表
     * @return 格式化进度文本，供注入 LLM 和前端
     */
    public String updatePlan(String key, List<PlanItem> items) {
        if (items == null || items.isEmpty()) {
            plans.remove(key);
            return "计划已清空。";
        }
        if (items.size() > MAX_PLAN_ITEMS) {
            items = new ArrayList<>(items.subList(0, MAX_PLAN_ITEMS));
        }
        validateDag(items);
        PlanState state = new PlanState(items);
        plans.put(key, state);
        log.debug("PlanTracker 更新计划: key={}, items={}, done={}/{}",
                key, items.size(), state.doneCount(), items.size());
        return generateProgressText(key);
    }

    /**
     * 工具执行后记录一次活动（非 updatePlan 类触发 nag 递进）。
     *
     * @param key 计划 key
     */
    public void onToolExecuted(String key) {
        PlanState state = plans.get(key);
        if (state != null) {
            state.nagCount++;
        }
    }

    /**
     * 检查是否需要追加 Nag 提醒到工具结果中。
     *
     * @param key 计划 key
     * @return Nag 提醒文本；null 表示无需提醒
     */
    public String buildNagIfNeeded(String key) {
        PlanState state = plans.get(key);
        if (state == null || state.nagCount < NAG_THRESHOLD) {
            return null;
        }
        return "\n\n<reminder>你已经连续执行了 " + state.nagCount
                + " 个工具，请调用 updatePlan 更新当前计划（含各任务状态），"
                + "确保与你的实际进度一致。</reminder>";
    }

    /**
     * 生成计划进度文本，用于注入 LLM 上下文和前端展示。
     *
     * @param key 计划 key
     * @return 进度文本；无计划时返回空字符串
     */
    public String generateProgressText(String key) {
        PlanState state = plans.get(key);
        if (state == null || state.items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("当前修改计划（").append(state.doneCount()).append("/").append(state.items.size()).append("）:\n");
        for (PlanItem item : state.items) {
            String mark = "completed".equals(item.status()) ? "[x]" : "[ ]";
            sb.append(mark).append(" #").append(item.id());
            if (item.text() != null && !item.text().isBlank()) {
                sb.append(" ").append(item.text());
            }
            if (item.deps() != null && !item.deps().isEmpty()) {
                sb.append(" (依赖: ").append(String.join(", ", item.deps().stream().map(Object::toString).toList())).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 清除计划（任务结束或停止时调用）。
     *
     * @param key 计划 key
     */
    public void clear(String key) {
        plans.remove(key);
    }

    /**
     * DAG 验证：deps 必须指向已有 id，且 in_progress 任务的 deps 必须全部 completed。
     */
    private void validateDag(List<PlanItem> items) {
        Map<Integer, String> statusMap = new LinkedHashMap<>();
        for (PlanItem item : items) {
            statusMap.put(item.id(), item.status() != null ? item.status() : "pending");
        }
        for (PlanItem item : items) {
            if (item.deps() == null || item.deps().isEmpty()) continue;
            for (Object dep : item.deps()) {
                int depId = dep instanceof Number ? ((Number) dep).intValue() : Integer.parseInt(dep.toString());
                if (!statusMap.containsKey(depId)) {
                    throw new IllegalArgumentException("计划项 #" + item.id() + " 的依赖 #" + depId + " 不存在");
                }
            }
            if ("in_progress".equals(item.status())) {
                for (Object dep : item.deps()) {
                    int depId = dep instanceof Number ? ((Number) dep).intValue() : Integer.parseInt(dep.toString());
                    if (!"completed".equals(statusMap.get(depId))) {
                        throw new IllegalArgumentException("计划项 #" + item.id()
                                + " 状态为 in_progress，但其依赖 #" + depId + " 尚未完成");
                    }
                }
            }
        }
    }

    /** 内存计划状态。 */
    private static class PlanState {
        final List<PlanItem> items;
        int nagCount;

        PlanState(List<PlanItem> items) {
            this.items = List.copyOf(items);
        }

        long doneCount() {
            return items.stream().filter(i -> "completed".equals(i.status())).count();
        }
    }
}