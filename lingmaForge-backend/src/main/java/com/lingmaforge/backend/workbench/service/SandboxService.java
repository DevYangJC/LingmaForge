package com.lingmaforge.backend.workbench.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lingmaforge.backend.infra.config.LingmaSandboxProperties;
import com.lingmaforge.backend.common.model.BuildResult;
import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.common.model.SandboxInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 沙箱服务：构建与预览管理。
 *
 * <p>构建：执行 npm install + npm run build，输出逐行推 SSE。
 * 预览：内嵌轻量 HTTP 服务器直接 serve {@code dist/} 静态文件，
 * 不再依赖外部 Nginx / Docker / Vite preview。</p>
 */
@Service
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);

    private static final int INSTALL_TIMEOUT_SECONDS = 300;
    private static final int BUILD_TIMEOUT_SECONDS = 180;
    private static final String MIME_HTML = "text/html; charset=utf-8";
    private static final String MIME_JS   = "application/javascript; charset=utf-8";
    private static final String MIME_CSS  = "text/css; charset=utf-8";
    private static final String MIME_JSON = "application/json; charset=utf-8";
    private static final String MIME_SVG  = "image/svg+xml";
    private static final String MIME_PNG  = "image/png";
    private static final String MIME_OCTET = "application/octet-stream";

    private final LingmaSandboxProperties properties;
    private final ProjectService projectService;
    private final ConcurrentHashMap<Long, SandboxInfo> sandboxMap = new ConcurrentHashMap<>();
    private HttpServer activeServer;

    public SandboxService(LingmaSandboxProperties properties, ProjectService projectService) {
        this.properties = properties;
        this.projectService = projectService;
    }

    public BuildResult npmBuild(Long projectId) {
        return npmBuild(projectId, null);
    }

    public BuildResult npmBuild(Long projectId, Consumer<String> logEmitter) {
        if (!properties.buildEnabled()) {
            log.info("构建开关关闭: projectId={}", projectId);
            return new BuildResult(BuildStatus.SUCCESS, "构建开关关闭", null, 0L);
        }
        Path workspace = projectService.getProjectWorkspace(projectId);
        long start = System.currentTimeMillis();

        try {
            String npmBin = npmBinary();
            emitLog(logEmitter, "安装依赖包... (npm install)");

            ProcessResult install = execCommand(workspace, npmBin, logEmitter, "install", "--no-audit");
            if (install.exitCode() != 0) {
                return new BuildResult(BuildStatus.FAILED, install.output(),
                        "npm install 失败", elapsed(start));
            }

            // vue-tsc 快速类型检查（~5s），比 npm build 快 10-30 倍
            emitLog(logEmitter, "TypeScript 类型检查... (vue-tsc --noEmit)");
            ProcessResult tsc = execCommand(workspace, npmBin, logEmitter,
                    "exec", "vue-tsc", "--noEmit");
            if (tsc.exitCode() != 0) {
                return new BuildResult(BuildStatus.FAILED, tsc.output(),
                        "TypeScript 类型检查失败", elapsed(start));
            }

            emitLog(logEmitter, "构建中... (npm run build)");
            ProcessResult build = execCommand(workspace, npmBin, logEmitter, "run", "build");
            long duration = elapsed(start);

            if (build.exitCode() != 0) {
                return new BuildResult(BuildStatus.FAILED, build.output(),
                        "npm run build 失败", duration);
            }

            if (!Files.isDirectory(workspace.resolve("dist"))) {
                return new BuildResult(BuildStatus.FAILED, build.output(),
                        "构建完成但 dist/ 目录未生成（可能是自定义 outDir）", duration);
            }

            emitLog(logEmitter, "构建成功（" + duration + "ms）");
            return new BuildResult(BuildStatus.SUCCESS, build.output(), null, duration);
        } catch (Exception e) {
            log.error("构建异常: projectId={}", projectId, e);
            return new BuildResult(BuildStatus.FAILED, null, e.getMessage(), elapsed(start));
        }
    }

    /**
     * 启动预览——创建内嵌 HTTP 服务器 serve {@code dist/} 静态文件。
     * 若默认端口被占用，自动尝试下一个端口（最多 10 次）。
     */
    public SandboxInfo startDevServer(Long projectId) {
        Path distDir = projectService.getProjectWorkspace(projectId).resolve("dist");
        if (!Files.isDirectory(distDir)) {
            log.warn("dist 目录不存在: projectId={}", projectId);
            return new SandboxInfo(null, 0, "stopped");
        }

        stopActiveServer();

        int basePort = properties.previewPort();
        int maxAttempts = 10;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int port = basePort + attempt;
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/", exchange -> serveStatic(distDir, exchange));
                server.setExecutor(null);
                server.start();
                this.activeServer = server;

                String url = "http://localhost:" + port;
                SandboxInfo info = new SandboxInfo(url, port, "running");
                sandboxMap.put(projectId, info);
                log.info("预览服务启动: {}, dist={} (尝试了 {} 次)", url, distDir, attempt + 1);
                return info;
            } catch (IOException e) {
                log.warn("端口 {} 被占用，尝试下一个... (attempt {}/{})", port, attempt + 1, maxAttempts);
            }
        }

        log.error("预览服务启动失败：所有端口 {}-{} 均被占用", basePort, basePort + maxAttempts - 1);
        return new SandboxInfo(null, 0, "error");
    }

    public void stopDevServer(Long projectId) {
        sandboxMap.remove(projectId);
        stopActiveServer();
    }

    public SandboxInfo getStatus(Long projectId) {
        return sandboxMap.getOrDefault(projectId,
                new SandboxInfo(null, 0, "stopped"));
    }

    private void stopActiveServer() {
        if (activeServer != null) {
            activeServer.stop(0);
            activeServer = null;
            log.info("已停止旧预览服务");
        }
    }

    /**
     * 静态文件服务——映射 URI 到 {@code dist/} 目录下的文件。
     * 对于 SPA 路由，非文件请求全部落到 {@code index.html}。
     */
    private void serveStatic(Path distDir, HttpExchange exchange) throws IOException {
        String uri = exchange.getRequestURI().getPath();
        if (uri.equals("/")) uri = "/index.html";

        Path file = distDir.resolve(uri.substring(1)).normalize();
        if (!file.startsWith(distDir)) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        if (!Files.isRegularFile(file) || Files.isHidden(file)) {
            // SPA fallback: 路由请求落到 index.html
            file = distDir.resolve("index.html");
            if (!Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
        }

        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", mimeType(file.toString()));
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String mimeType(String name) {
        if (name.endsWith(".html")) return MIME_HTML;
        if (name.endsWith(".js") || name.endsWith(".mjs")) return MIME_JS;
        if (name.endsWith(".css")) return MIME_CSS;
        if (name.endsWith(".json")) return MIME_JSON;
        if (name.endsWith(".svg")) return MIME_SVG;
        if (name.endsWith(".png")) return MIME_PNG;
        if (name.endsWith(".ico")) return "image/x-icon";
        return MIME_OCTET;
    }

    private String npmBinary() {
        String binary = properties.npmBinary();
        if (binary == null || binary.isBlank()) {
            binary = System.getProperty("os.name").toLowerCase().contains("windows") ? "npm.cmd" : "npm";
        }
        return binary;
    }

    private ProcessResult execCommand(Path workspace, String command, Consumer<String> logEmitter,
            String... args) throws IOException, InterruptedException {
        String[] cmd = new String[args.length + 1];
        cmd[0] = command;
        System.arraycopy(args, 0, cmd, 1, args.length);

        log.debug("执行: {} (workspace: {})", String.join(" ", cmd), workspace);

        Process process = new ProcessBuilder(cmd)
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            java.util.regex.Pattern important = java.util.regex.Pattern.compile(
                    "(?i)(error|warn|ERR|fail|success|compiled|building|done)");
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                lineCount++;
                if (logEmitter != null) {
                    if (important.matcher(line).find() || lineCount % 20 == 0) {
                        logEmitter.accept(line);
                    }
                }
            }
        }

        int timeoutSeconds = args.length > 0 && "install".equals(args[0])
                ? INSTALL_TIMEOUT_SECONDS : BUILD_TIMEOUT_SECONDS;

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            String msg = "构建超时（" + timeoutSeconds + "s）";
            emitLog(logEmitter, msg);
            return new ProcessResult(-1, msg);
        }
        return new ProcessResult(process.exitValue(), output.toString());
    }

    private void emitLog(Consumer<String> logEmitter, String text) {
        if (logEmitter != null) {
            logEmitter.accept(text);
        }
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private record ProcessResult(int exitCode, String output) {
    }
}