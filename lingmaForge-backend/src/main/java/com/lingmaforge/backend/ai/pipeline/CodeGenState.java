package com.lingmaforge.backend.ai.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import com.lingmaforge.backend.model.BuildStatus;
import com.lingmaforge.backend.model.GeneratedFile;
import com.lingmaforge.backend.model.PlanResult;
import com.lingmaforge.backend.model.RequirementSpec;

/**
 * 灵码工坊代码生成流水线的状态定义。
 *
 * <p>继承 LangGraph4j 的 {@link AgentState}，作为各节点共享的"黑板"。
 * 每个字段通过 {@link Channel} 声明类型与合并策略：
 * <ul>
 *   <li>{@link Channels#base(java.util.function.Supplier)} —— LastValue 覆盖合并，用于单值字段</li>
 *   <li>{@link Channels#appender(java.util.function.Supplier)} —— 追加合并，用于列表字段</li>
 * </ul>
 * 节点之间不直接传参，而是通过本状态间接传递，支持构建失败回退等非线性流转。</p>
 */
public class CodeGenState extends AgentState {

    /** 用户原始需求文本。 */
    public static final String PROMPT = "prompt";
    /** 项目 ID。 */
    public static final String PROJECT_ID = "projectId";
    /** 任务 ID（= SSE streamId）。 */
    public static final String TASK_ID = "taskId";
    /** 需求分析结果。 */
    public static final String ANALYSIS_RESULT = "analysisResult";
    /** 执行规划结果。 */
    public static final String PLAN_RESULT = "planResult";
    /** 当前正在生成的文件序号。 */
    public static final String CURRENT_FILE_INDEX = "currentFileIndex";
    /** 已生成的文件列表（追加合并）。 */
    public static final String GENERATED_FILES = "generatedFiles";
    /** 构建状态：SUCCESS / FAILED。 */
    public static final String BUILD_STATUS = "buildStatus";
    /** 构建错误信息。 */
    public static final String BUILD_ERROR = "buildError";
    /** 构建耗时（秒）。 */
    public static final String BUILD_TIME = "buildTime";
    /** 回退重试次数。 */
    public static final String RETRY_COUNT = "retryCount";
    /** 预览 URL。 */
    public static final String PREVIEW_URL = "previewUrl";
    /** 预览端口。 */
    public static final String PREVIEW_PORT = "previewPort";

    public CodeGenState(Map<String, Object> data) {
        super(data);
    }

    /**
     * 声明所有状态字段的 Channel 定义。
     *
     * @return 字段名到 Channel 的映射
     */
    public static Map<String, Channel<?>> channels() {
        return Map.ofEntries(
                Map.entry(PROMPT, Channels.base(() -> null)),
                Map.entry(PROJECT_ID, Channels.base(() -> null)),
                Map.entry(TASK_ID, Channels.base(() -> null)),
                Map.entry(ANALYSIS_RESULT, Channels.base(() -> null)),
                Map.entry(PLAN_RESULT, Channels.base(() -> null)),
                Map.entry(CURRENT_FILE_INDEX, Channels.base(() -> 0)),
                // 已生成文件：追加合并，每轮代码生成追加一个文件
                Map.entry(GENERATED_FILES, Channels.appender(List::of)),
                Map.entry(BUILD_STATUS, Channels.base(() -> BuildStatus.PENDING)),
                Map.entry(BUILD_ERROR, Channels.base(() -> null)),
                Map.entry(BUILD_TIME, Channels.base(() -> 0)),
                Map.entry(RETRY_COUNT, Channels.base(() -> 0)),
                Map.entry(PREVIEW_URL, Channels.base(() -> null)),
                Map.entry(PREVIEW_PORT, Channels.base(() -> 0)));
    }

    /** 读取用户原始需求。 */
    public Optional<String> prompt() {
        return value(PROMPT);
    }

    /** 读取项目 ID。 */
    public Optional<String> projectId() {
        return value(PROJECT_ID);
    }

    /** 读取任务 ID。 */
    public Optional<String> taskId() {
        return value(TASK_ID);
    }

    /** 读取需求分析结果。 */
    public Optional<RequirementSpec> analysisResult() {
        return value(ANALYSIS_RESULT);
    }

    /** 读取执行规划结果。 */
    public Optional<PlanResult> planResult() {
        return value(PLAN_RESULT);
    }

    /** 读取当前正在生成的文件序号。 */
    public Optional<Integer> currentFileIndex() {
        return value(CURRENT_FILE_INDEX);
    }

    /** 读取已生成的文件列表。 */
    public Optional<List<GeneratedFile>> generatedFiles() {
        return value(GENERATED_FILES);
    }

    /** 读取构建状态。 */
    public Optional<BuildStatus> buildStatus() {
        return value(BUILD_STATUS);
    }

    /** 读取构建错误信息。 */
    public Optional<String> buildError() {
        return value(BUILD_ERROR);
    }

    /** 读取构建耗时。 */
    public Optional<Integer> buildTime() {
        return value(BUILD_TIME);
    }

    /** 读取回退重试次数。 */
    public Optional<Integer> retryCount() {
        return value(RETRY_COUNT);
    }

    /** 读取预览 URL。 */
    public Optional<String> previewUrl() {
        return value(PREVIEW_URL);
    }

    /** 读取预览端口。 */
    public Optional<Integer> previewPort() {
        return value(PREVIEW_PORT);
    }
}
