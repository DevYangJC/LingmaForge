package com.lingmaforge.backend.workbench.ai.node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.common.model.BuildResult;
import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.workbench.service.SandboxService;

/**
 * 节点五：构建验证。
 *
 * <p>在沙箱中执行 npm install + npm run build，根据结果更新构建状态。
 * 构建成功 → 条件边路由到预览部署；构建失败 → 条件边回退到代码生成修复。
 * 纯逻辑节点，不调用 LLM。</p>
 */
@Component
public class BuildVerificationNode extends AbstractCodeGenNode {

    private static final Logger log = LoggerFactory.getLogger(BuildVerificationNode.class);

    /** 节点名称。 */
    public static final String NODE_NAME = "build_verification";

    private final SandboxService sandboxService;

    public BuildVerificationNode(SandboxService sandboxService, GenerationStreamRegistry streamRegistry) {
        super(streamRegistry);
        this.sandboxService = sandboxService;
    }

    /**
     * 执行构建验证。
     *
     * @param state 流水线状态
     * @return 状态更新：buildStatus / buildTime / buildError / retryCount
     */
    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在进行代码构建与类型验证...");
        Long projectId = projectId(state);
        try {
            BuildResult result = sandboxService.npmBuild(projectId, emitter::emitLog);
            int buildSeconds = (int) (result.durationMillis() / 1000);

            Map<String, Object> updates = new HashMap<>();
            if (result.status() == BuildStatus.SUCCESS) {
                emitter.emitLog("构建成功（" + buildSeconds + "s）");
                updates.put(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS);
                updates.put(CodeGenState.BUILD_TIME, buildSeconds);
                updates.put(CodeGenState.BUILD_ERROR, null);
            } else {
                String rawError = result.error() == null ? result.output() : result.error();
                String error = summarizeBuildError(rawError);
                emitter.emitLog("构建失败: " + error);
                int retryCount = state.retryCount().orElse(0) + 1;
                updates.put(CodeGenState.BUILD_STATUS, BuildStatus.FAILED);
                updates.put(CodeGenState.BUILD_ERROR, error);
                updates.put(CodeGenState.RETRY_COUNT, retryCount);
            }
            return updates;
        } catch (Exception e) {
            log.error("[{}] 构建验证异常", state.taskId().orElse(""), e);
            emitter.emitLog("构建验证异常: " + e.getMessage());
            Map<String, Object> updates = new HashMap<>();
            updates.put(CodeGenState.BUILD_STATUS, BuildStatus.FAILED);
            updates.put(CodeGenState.BUILD_ERROR, e.getMessage());
            updates.put(CodeGenState.RETRY_COUNT, state.retryCount().orElse(0) + 1);
            return updates;
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }

    /**
     * 把 npm 构建输出裁剪为 LLM 能理解的关键错误摘要。
     *
     * <p>npm 构建输出通常 1000+ 行，直接注入 prompt 会让 LLM 无法定位问题。
     * 本方法只保留：包含文件名路径的行、包含 'error'/'Error'/'ERROR' 的行、
     * 'Could not resolve' / 'Module not found' 等关键短语、以及最后 3 行。</p>
     */
    static String summarizeBuildError(String raw) {
        if (raw == null || raw.isBlank()) return "构建失败（无错误输出）";

        Pattern errorLine = Pattern.compile("(?i)(error|fail|could not resolve|module not found|cannot find|is not a module|unexpected token|unknown word|syntax error|type.*error|missing.*export)");
        Pattern pathLine = Pattern.compile("\\S+\\.(vue|ts|tsx|js|jsx|css|scss|json|html)[:\\d:]*");

        List<String> important = new ArrayList<>();
        String[] lines = raw.split("\n");
        int limit = Math.min(lines.length, 200);

        for (int i = 0; i < limit; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            boolean isError = errorLine.matcher(line).find();
            boolean hasPath = pathLine.matcher(line).find();

            if (isError || hasPath) {
                if (line.length() > 300) line = line.substring(0, 300) + "...";
                important.add(line);
            }
        }

        // 保留最后 3 行（通常是 npm 的总结信息）
        int lastStart = Math.max(0, lines.length - 3);
        for (int i = lastStart; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty() && !important.contains(line)) {
                if (line.length() > 300) line = line.substring(0, 300) + "...";
                important.add(line);
            }
        }

        if (important.isEmpty()) {
            // 真找不到错误详情，返回前 10 行
            StringBuilder fallback = new StringBuilder("构建失败，以下是前几行输出：\n");
            for (int i = 0; i < Math.min(10, lines.length); i++) {
                fallback.append(lines[i]).append('\n');
            }
            return fallback.toString();
        }

        // 去重 + 限制总长度
        return String.join("\n", important.stream().distinct().toList());
    }
}
