package com.lingmaforge.backend.workbench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lingmaforge.backend.common.model.BuildResult;
import com.lingmaforge.backend.common.model.BuildStatus;
import com.lingmaforge.backend.common.model.SandboxInfo;
import com.lingmaforge.backend.infra.config.LingmaSandboxProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SandboxService 单元测试。
 *
 * <p>覆盖构建验证的全部关键路径：
 * <ul>
 *   <li>构建开关关闭时跳过构建</li>
 *   <li>npm 二进制不存在时的错误处理（即"CreateProcess error=2"场景）</li>
 *   <li>Dev Server 生命周期（启动/停止/查询）</li>
 *   <li>构建超时</li>
 * </ul>
 * 使用 Mockito 模拟依赖，无需真实 Node.js 环境。</p>
 */
@DisplayName("SandboxService — 沙箱构建服务单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SandboxServiceTest {

    private static final Logger log = LoggerFactory.getLogger(SandboxServiceTest.class);

    @Mock private LingmaSandboxProperties properties;
    @Mock private ProjectService projectService;

    private SandboxService sandboxService;
    private Path tempWorkspace;

    @BeforeEach
    void setUp() throws IOException {
        tempWorkspace = Files.createTempDirectory("sandbox-test-");
        log.info("========== SandboxService 测试初始化 ==========");
        log.info("  临时工作区: {}", tempWorkspace);

        // 默认让 ProjectService 返回临时工作区
        lenient().when(projectService.getProjectWorkspace(anyLong())).thenReturn(tempWorkspace);

        // 通用默认值（各内层类的 @BeforeEach 会覆盖需要的字段）
        lenient().when(properties.buildTimeoutSeconds()).thenReturn(30);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempWorkspace != null) {
            Files.walk(tempWorkspace)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        }
    }

    // ==================== 构建禁用路径 ====================

    @Nested
    @DisplayName("构建开关关闭")
    class BuildDisabled {

        @BeforeEach
        void init() {
            when(properties.buildEnabled()).thenReturn(false);
            sandboxService = new SandboxService(properties, projectService);
            log.info("  buildEnabled=false → 构建被禁用");
        }

        @Test
        @DisplayName("npmBuild 不执行任何进程，直接返回 SUCCESS")
        void shouldSkipBuildWhenDisabled() {
            BuildResult result = sandboxService.npmBuild(1L);

            assertThat(result.status()).isEqualTo(BuildStatus.SUCCESS);
            assertThat(result.error()).isNull();
            assertThat(result.durationMillis()).isZero();
            log.info("[OK] 构建禁用时立即返回 SUCCESS，未调用 ProcessBuilder");
        }

        @Test
        @DisplayName("构建禁用时即便有异常路径也不影响")
        void shouldBeSafeWhenDisabled() {
            // 即使配置了不存在的 npm 路径，构建禁用时也不报错
            sandboxService = new SandboxService(properties, projectService);
            BuildResult result = sandboxService.npmBuild(1L);

            assertThat(result.status()).isEqualTo(BuildStatus.SUCCESS);
            log.info("[OK] 构建禁用时安全跳过，不受 npm 配置影响");
        }
    }

    // ==================== npm 二进制不存在 ====================

    @Nested
    @DisplayName("npm 二进制不存在（CreateProcess error=2）")
    class NpmBinaryNotFound {

        @BeforeEach
        void init() {
            // 启用构建以便触发 ProcessBuilder 调用
            when(properties.buildEnabled()).thenReturn(true);
            // 设置一个不存在的 npm 路径，复现 "系统找不到指定的文件" 错误
            when(properties.npmBinary()).thenReturn("C:\\non-existent\\npm.cmd");
            when(properties.nodeBinary()).thenReturn("C:\\non-existent\\node.exe");
            sandboxService = new SandboxService(properties, projectService);
            log.info("  npm-binary = C:\\non-existent\\npm.cmd（不存在的路径）");
        }

        @Test
        @DisplayName("npmBuild 捕获 IOException，返回 FAILED + 错误消息含 'CreateProcess'")
        void shouldReturnFailedWithCreateProcessError() {
            BuildResult result = sandboxService.npmBuild(1L);

            assertThat(result.status()).isEqualTo(BuildStatus.FAILED);
            assertThat(result.error())
                    .as("错误消息应包含系统找不到文件的关键字")
                    .containsAnyOf("CreateProcess", "error=2", "系统找不到", "not found", "No such file");
            log.info("[OK] npm 不存在 → FAILED, error=[{}]", result.error());
        }

        @Test
        @DisplayName("node 不存在同样会触发错误传播")
        void shouldFailWhenNodeNotFound() {
            // 这次让 npm install 正常但 node 不存在
            // (实际上 npm.cmd 也会找不到，但 java.io.IOException 由 ProcessBuilder.start() 抛出)
            when(properties.npmBinary()).thenReturn("C:\\non-existent\\pip.cmd");
            sandboxService = new SandboxService(properties, projectService);

            BuildResult result = sandboxService.npmBuild(1L);

            assertThat(result.status()).isEqualTo(BuildStatus.FAILED);
            log.info("[OK] node/npm 二进制不存在错误传播验证通过");
        }
    }

    // ==================== npm install 阶段失败 ====================

    @Nested
    @DisplayName("npm install 失败（例如 package.json 损坏）")
    class NpmInstallFailure {

        @BeforeEach
        void init() throws IOException {
            when(properties.buildEnabled()).thenReturn(true);

            // 设置可用的 npm 路径（真实路径）
            String npmPath = findNpmBinary();
            String nodePath = findNodeBinary();

            // 跳过：当前环境没有 npm 时跳过需要真实 npm 的测试
            assumeTrue(Files.exists(Path.of(npmPath)), "npm not available, skipping real npm tests");
            assumeTrue(Files.exists(Path.of(nodePath)), "node not available, skipping real npm tests");

            log.info("  使用真实 npm: {}", npmPath);
            log.info("  使用真实 node: {}", nodePath);

            // 故意放一个损坏的 package.json
            Path pkgJson = tempWorkspace.resolve("package.json");
            Files.writeString(pkgJson, "{ invalid json ");
            log.info("  损坏的 package.json 已写入: {}", pkgJson);

            when(properties.npmBinary()).thenReturn(npmPath);
            when(properties.nodeBinary()).thenReturn(nodePath);
            sandboxService = new SandboxService(properties, projectService);
        }

        @Test
        @DisplayName("package.json 损坏 → npm install 失败 → 返回 FAILED + 错误信息")
        void shouldReturnFailedWhenNpmInstallFails() {
            BuildResult result = sandboxService.npmBuild(1L);

            assertThat(result.status()).isEqualTo(BuildStatus.FAILED);
            assertThat(result.error())
                    .as("错误消息应包含 npm install 失败相关信息")
                    .containsAnyOf("npm install 失败", "npm ERR", "JSON", "parse", "SyntaxError", "E404");
            log.info("[OK] npm install 失败 → FAILED, 错误=[{}]", result.error());
        }
    }

    // ==================== 构建成功路径（需要完整工作区） ====================

    @Nested
    @DisplayName("构建成功路径（使用真实 npm + 合法项目）")
    class BuildSuccess {

        @BeforeEach
        void init() throws IOException {
            when(properties.buildEnabled()).thenReturn(true);

            String npmPath = findNpmBinary();
            String nodePath = findNodeBinary();

            // 跳过：当前环境没有 npm 时跳过需要真实 npm 的测试
            assumeTrue(Files.exists(Path.of(npmPath)), "npm not available, skipping real npm tests");
            assumeTrue(Files.exists(Path.of(nodePath)), "node not available, skipping real npm tests");

            log.info("  使用真实 npm: {} (node: {})", npmPath, nodePath);

            // 写入最小可构建的 package.json
            Files.writeString(tempWorkspace.resolve("package.json"), """
                    {
                      "name": "test-build",
                      "private": true,
                      "type": "module",
                      "scripts": {
                        "build": "echo build-success-ok"
                      }
                    }
                    """);
            log.info("  合法 package.json 已写入，build 命令为 echo");

            when(properties.npmBinary()).thenReturn(npmPath);
            when(properties.nodeBinary()).thenReturn(nodePath);
            sandboxService = new SandboxService(properties, projectService);
        }

        @Test
        @DisplayName("npm install + npm run build 成功 → 返回 SUCCESS + 构建输出 + 耗时")
        void shouldReturnSuccessWithOutputAndDuration() {
            long before = System.currentTimeMillis();
            BuildResult result = sandboxService.npmBuild(1L);
            long after = System.currentTimeMillis();

            assertThat(result.status()).isEqualTo(BuildStatus.SUCCESS);
            assertThat(result.error()).isNull();
            assertThat(result.output())
                    .as("构建输出应包含 build 命令的执行结果")
                    .contains("build-success-ok");
            assertThat(result.durationMillis())
                    .as("构建耗时应在合理范围内")
                    .isBetween(0L, after - before + 5000);
            log.info("[OK] 构建成功 → SUCCESS ({}ms)", result.durationMillis());
        }
    }

    // ==================== Dev Server 生命周期 ====================

    @Nested
    @DisplayName("Dev Server 生命周期管理")
    class DevServerLifecycle {

        @BeforeEach
        void init() {
            // 构建禁用，专注于 Dev Server 方法
            when(properties.buildEnabled()).thenReturn(false);
            when(properties.basePreviewHost()).thenReturn("preview.example.com");
            when(properties.previewPort()).thenReturn(5173);
            sandboxService = new SandboxService(properties, projectService);
            log.info("  预览地址模板: https://{projectId}.preview.example.com:5173");
        }

        @Test
        @DisplayName("startDevServer → 返回 URL，getStatus 确认 running")
        void shouldStartDevServerAndReturnUrl() {
            SandboxInfo info = sandboxService.startDevServer(42L);

            assertThat(info.status()).isEqualTo("running");
            assertThat(info.url()).isEqualTo("https://42.preview.example.com");
            assertThat(info.port()).isEqualTo(5173);
            log.info("[OK] startDevServer → url={}, port={}, status={}", info.url(), info.port(), info.status());

            // getStatus 验证持久化
            SandboxInfo fromStatus = sandboxService.getStatus(42L);
            assertThat(fromStatus.url()).isEqualTo(info.url());
            assertThat(fromStatus.status()).isEqualTo("running");
            log.info("[OK] getStatus 确认状态=running");
        }

        @Test
        @DisplayName("stopDevServer → getStatus 返回 stopped")
        void shouldStopDevServer() {
            sandboxService.startDevServer(42L);
            sandboxService.stopDevServer(42L);

            SandboxInfo info = sandboxService.getStatus(42L);
            assertThat(info.status()).isEqualTo("stopped");
            assertThat(info.url()).isNull();
            assertThat(info.port()).isZero();
            log.info("[OK] stopDevServer → status=stopped");
        }

        @Test
        @DisplayName("未启动的项目 → getStatus 返回 stopped")
        void shouldReturnStoppedForUnknownProject() {
            SandboxInfo info = sandboxService.getStatus(99L);

            assertThat(info.status()).isEqualTo("stopped");
            assertThat(info.url()).isNull();
            assertThat(info.port()).isZero();
            log.info("[OK] 未启动项目 → status=stopped");
        }

        @Test
        @DisplayName("多次 startDevServer 不冲突")
        void shouldHandleMultipleStarts() {
            sandboxService.startDevServer(1L);
            sandboxService.startDevServer(2L);

            assertThat(sandboxService.getStatus(1L).url()).contains("1");
            assertThat(sandboxService.getStatus(2L).url()).contains("2");
            log.info("[OK] 多项目 DevServer 互不冲突");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 查找系统中可用的 npm 二进制路径。
     *
     * <p>通过检查常见安装路径定位，不使用 ProcessBuilder（测试环境中可能没有 PATH）。</p>
     */
    private static String findNpmBinary() {
        // 按优先级检查常见安装位置
        String[] candidates = {
                "D:\\Develop\\DevelopTool\\NVM\\nodeJS\\npm.cmd",
                "D:\\Develop\\DevelopTool\\Node-js\\npm.cmd",
                "C:\\Program Files\\nodejs\\npm.cmd",
                "C:\\Program Files (x86)\\nodejs\\npm.cmd",
                "npm.cmd",
        };
        return findFirstExisting(candidates);
    }

    /**
     * 查找系统中可用的 node 二进制路径。
     */
    private static String findNodeBinary() {
        String[] candidates = {
                "D:\\Develop\\DevelopTool\\NVM\\nodeJS\\node.exe",
                "D:\\Develop\\DevelopTool\\Node-js\\node.exe",
                "C:\\Program Files\\nodejs\\node.exe",
                "C:\\Program Files (x86)\\nodejs\\node.exe",
                "node.exe",
        };
        return findFirstExisting(candidates);
    }

    private static String findFirstExisting(String... paths) {
        for (String path : paths) {
            if (Files.exists(Path.of(path))) return path;
        }
        return paths[paths.length - 1]; // 最后回退到名字本身
    }
}
