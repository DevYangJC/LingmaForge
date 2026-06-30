package com.lingmaforge.backend.workbench.ai.node;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.backend.workbench.ai.pipeline.CodeGenState;
import com.lingmaforge.backend.common.model.SandboxInfo;
import com.lingmaforge.backend.workbench.service.ProjectService;
import com.lingmaforge.backend.workbench.service.SandboxService;

/**
 * 节点六：预览部署。
 *
 * <p>构建验证通过后启动 Dev Server，把预览 URL 写入状态。纯逻辑节点，不调用 LLM。
 * M3 阶段将启动真实 Vite Dev Server；当前实现基于配置返回预览地址。</p>
 */
@Component
public class PreviewDeployNode extends AbstractCodeGenNode {

    /** 节点名称。 */
    public static final String NODE_NAME = "preview_deploy";

    private final SandboxService sandboxService;
    private final ProjectService projectService;

    public PreviewDeployNode(SandboxService sandboxService,
            ProjectService projectService,
            GenerationStreamRegistry streamRegistry) {
        super(streamRegistry);
        this.sandboxService = sandboxService;
        this.projectService = projectService;
    }

    /**
     * 执行预览部署。
     *
     * @param state 流水线状态
     * @return 状态更新：previewUrl / previewPort
     */
    public Map<String, Object> execute(CodeGenState state) {
        GenerationStreamEmitter emitter = setupContext(state, NODE_NAME, "正在启动预览开发服务器...");
        Long projectId = projectId(state);
        try {
            SandboxInfo sandbox = sandboxService.startDevServer(projectId);
            projectService.updateBuildResult(projectId, "SUCCESS", sandbox.url());
            emitter.emitLog("开发服务器运行中 " + sandbox.url());
            return Map.of(
                    CodeGenState.PREVIEW_URL, sandbox.url(),
                    CodeGenState.PREVIEW_PORT, sandbox.port());
        } finally {
            completeNode(emitter, NODE_NAME);
        }
    }
}
