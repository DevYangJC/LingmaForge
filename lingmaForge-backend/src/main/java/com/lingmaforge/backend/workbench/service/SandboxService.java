package com.lingmaforge.backend.workbench.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lingmaforge.backend.infra.config.LingmaSandboxProperties;
import com.lingmaforge.backend.common.model.BuildResult;
import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.common.model.SandboxInfo;

/**
 * 沙箱服务：负责在项目工作区执行构建并管理预览地址。
 *
 * <p>M3 阶段将替换为 Docker 容器沙箱；当前实现通过 {@link ProcessBuilder} 在本地工作区
 * 执行 npm install / npm run build，构建开关由 {@link LingmaSandboxProperties#buildEnabled()} 控制。</p>
 */
@Service
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);

    private final LingmaSandboxProperties properties;
    private final ProjectService projectService;
    private final ConcurrentHashMap<Long, SandboxInfo> sandboxMap = new ConcurrentHashMap<>();

    public SandboxService(LingmaSandboxProperties properties, ProjectService projectService) {
        this.properties = properties;
        this.projectService = projectService;
    }

    /**
     * 在项目工作区执行 npm install 与 npm run build。
     *
     * @param projectId 项目 ID
     * @return 构建结果
     */
    public BuildResult npmBuild(Long projectId) {
        if (!properties.buildEnabled()) {
            log.info("构建开关关闭，跳过真实构建: projectId={}", projectId);
            return new BuildResult(BuildStatus.SUCCESS, "构建开关关闭，跳过构建", null, 0L);
        }
        Path workspace = projectService.getProjectWorkspace(projectId);
        long start = System.currentTimeMillis();
        try {
            ProcessResult install = execCommand(workspace, properties.npmBinary(), "install", "--no-audit");
            if (install.exitCode() != 0) {
                return new BuildResult(BuildStatus.FAILED, install.output(),
                        "npm install 失败: " + install.output(), System.currentTimeMillis() - start);
            }
            ProcessResult build = execCommand(workspace, properties.npmBinary(), "run", "build");
            long duration = System.currentTimeMillis() - start;
            if (build.exitCode() == 0) {
                return new BuildResult(BuildStatus.SUCCESS, build.output(), null, duration);
            }
            return new BuildResult(BuildStatus.FAILED, build.output(), build.output(), duration);
        } catch (Exception e) {
            log.error("构建执行异常: projectId={}", projectId, e);
            return new BuildResult(BuildStatus.FAILED, null, e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * 启动预览（返回预览地址）。当前实现基于配置生成 URL，M3 阶段将启动真实 Vite Dev Server。
     *
     * @param projectId 项目 ID
     * @return 沙箱运行信息
     */
    public SandboxInfo startDevServer(Long projectId) {
        String url = "https://%s.%s".formatted(projectId, properties.basePreviewHost());
        SandboxInfo info = new SandboxInfo(url, properties.previewPort(), "running");
        sandboxMap.put(projectId, info);
        return info;
    }

    /**
     * 停止沙箱。
     *
     * @param projectId 项目 ID
     */
    public void stopDevServer(Long projectId) {
        sandboxMap.remove(projectId);
    }

    /**
     * 查询沙箱状态。
     *
     * @param projectId 项目 ID
     * @return 沙箱运行信息，不存在返回 stopped 状态
     */
    public SandboxInfo getStatus(Long projectId) {
        return sandboxMap.getOrDefault(projectId,
                new SandboxInfo(null, 0, "stopped"));
    }

    private ProcessResult execCommand(Path workspace, String command, String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = command;
        System.arraycopy(args, 0, cmd, 1, args.length);
        ProcessBuilder builder = new ProcessBuilder(cmd)
                .directory(workspace.toFile())
                .redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        boolean finished = process.waitFor(properties.buildTimeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(-1, "构建超时（" + properties.buildTimeoutSeconds() + "s）");
        }
        return new ProcessResult(process.exitValue(), output.toString());
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
