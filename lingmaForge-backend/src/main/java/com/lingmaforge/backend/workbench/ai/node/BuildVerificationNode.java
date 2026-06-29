package com.lingmaforge.backend.workbench.ai.node;

import java.util.HashMap;
import java.util.Map;

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
        GenerationStreamEmitter emitter = setupContext(state);
        Long projectId = projectId(state);
        try {
            emitter.emitLog("安装依赖包... (npm install)");
            BuildResult result = sandboxService.npmBuild(projectId);
            int buildSeconds = (int) (result.durationMillis() / 1000);

            Map<String, Object> updates = new HashMap<>();
            if (result.status() == BuildStatus.SUCCESS) {
                emitter.emitLog("构建成功（" + buildSeconds + "s）");
                updates.put(CodeGenState.BUILD_STATUS, BuildStatus.SUCCESS);
                updates.put(CodeGenState.BUILD_TIME, buildSeconds);
                updates.put(CodeGenState.BUILD_ERROR, null);
            } else {
                String error = result.error() == null ? result.output() : result.error();
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
            clearContext();
        }
    }
}
