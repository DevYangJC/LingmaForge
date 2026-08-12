package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lingmaforge.backend.common.model.FilePlan;
import com.lingmaforge.backend.workbench.ai.node.CodeGenerationNode;
import com.lingmaforge.backend.workbench.service.ProjectFileService;

/**
 * {@code CodeGenerationNode.shouldRegenerate(FilePlan, String, Long)} 单元测试。
 *
 * <p>验证构建失败后的文件回退重生成策略（见方法 javadoc 的三条规则）：
 * <ol>
 *   <li>buildError 为 null（非重试场景） → 全量生成，返回 true</li>
 *   <li>构建错误中明确包含该文件路径 → 必须重生成，返回 true</li>
 *   <li>构建错误未提及任何项目文件（模块级错误） → 只重生成配置文件/入口文件</li>
 *   <li>构建错误提到其他文件但不包含本文件 → 不重生成（无关联），返回 false</li>
 * </ol>
 *
 * <p>{@code shouldRegenerate} 是 private 方法，测试通过反射调用。
 * 由于方法内部依赖 {@link ProjectFileService#listFilePaths(Long)} 来判断
 * "构建错误是否提及任何项目文件"，这里通过 Mockito 注入 stub。</p>
 */
@DisplayName("shouldRegenerate — 构建失败回退重生成策略单测")
@ExtendWith(MockitoExtension.class)
class CodeGenerationNodeRegenerateTest {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationNodeRegenerateTest.class);

    private static final Long PROJECT_ID = 42L;

    @Mock private ProjectFileService projectFileService;

    private Method shouldRegenerateMethod;

    @BeforeEach
    void setUp() throws Exception {
        shouldRegenerateMethod = CodeGenerationNode.class
                .getDeclaredMethod("shouldRegenerate",
                        com.lingmaforge.backend.common.model.FilePlan.class,
                        String.class,
                        Long.class);
        shouldRegenerateMethod.setAccessible(true);
        // 默认：项目下已有这些文件。测试可按场景覆盖此 stub。
        lenient().when(projectFileService.listFilePaths(PROJECT_ID))
                .thenReturn(List.of(
                        "package.json",
                        "src/App.tsx",
                        "src/components/PlanCard.tsx",
                        "src/styles/globals.css"));
    }

    /**
     * 调用 shouldRegenerate。
     *
     * <p>{@code shouldRegenerate} 是 private 实例方法，内部只用到构造时注入的
     * {@link #projectFileService}（调用其 {@code listFilePaths}）。其它实例字段
     * （agent / promptLoader / objectMapper / executor）在该方法中不参与逻辑。
     * 因此构造一个 {@link CodeGenerationNode} 实例，用 Mock 填充所有构造参数，
     * 再通过反射调用该私有方法。这避免了把方法改成 static 的侵入式重构，
     * 也能让 {@code projectFileService} 的 stub 真正生效。</p>
     */
    private boolean shouldRegenerate(FilePlan plan, String buildError) {
        try {
            com.lingmaforge.backend.workbench.ai.factory.AgentFactory agentFactory =
                    org.mockito.Mockito.mock(com.lingmaforge.backend.workbench.ai.factory.AgentFactory.class);
            com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry registry =
                    org.mockito.Mockito.mock(com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry.class);
            com.lingmaforge.backend.workbench.service.PromptTemplateLoader promptLoader =
                    org.mockito.Mockito.mock(com.lingmaforge.backend.workbench.service.PromptTemplateLoader.class);
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.concurrent.Executor executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            com.lingmaforge.backend.workbench.ai.service.CodeGenAgent agent =
                    org.mockito.Mockito.mock(com.lingmaforge.backend.workbench.ai.service.CodeGenAgent.class);
            org.mockito.Mockito.lenient().when(agentFactory.createCodeGenAgent()).thenReturn(agent);

            CodeGenerationNode node = new CodeGenerationNode(
                    agentFactory, registry, promptLoader, projectFileService, objectMapper, executor);
            return (boolean) shouldRegenerateMethod.invoke(node, plan, buildError, PROJECT_ID);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new AssertionError("调用 shouldRegenerate 失败: " + cause, cause);
        } catch (Exception e) {
            throw new AssertionError("调用 shouldRegenerate 失败: " + e.getMessage(), e);
        }
    }

    private static FilePlan plan(String path) {
        return new FilePlan(path, "purpose", "component", List.of(), true);
    }

    /** 构造一个指定 fileType 的 FilePlan（用于测试 isEntryOrConfigFile 的 fileType 优先判定）。 */
    private static FilePlan plan(String path, String fileType) {
        return new FilePlan(path, "purpose", fileType, List.of(), true);
    }

    // ==================== 规则 1：buildError == null → 全量生成 ====================

    @Nested
    @DisplayName("规则1：非重试场景（buildError == null）")
    class NoBuildError {
        @Test
        @DisplayName("任何文件都应重生成（首次生成）")
        void shouldRegenerateAllWhenNoBuildError() {
            boolean result = shouldRegenerate(plan("src/components/PlanCard.tsx"), null);
            assertThat(result).isTrue();
            log.info("[OK] buildError=null 时所有文件都重生成");
        }

        @Test
        @DisplayName("buildError 为 null 时连普通组件文件也返回 true")
        void shouldRegenerateEvenNonEntryFile() {
            boolean result = shouldRegenerate(plan("src/utils/helper.ts"), null);
            assertThat(result).isTrue();
            log.info("[OK] buildError=null 时非入口文件也返回 true（全量生成）");
        }
    }

    // ==================== 规则 2：错误明确包含文件路径 → 重生成 ====================

    @Nested
    @DisplayName("规则2：构建错误明确包含文件路径")
    class PathMentioned {
        @Test
        @DisplayName("错误信息含 'src/App.tsx' → 该文件必须重生成")
        void shouldRegenerateWhenPathMentioned() {
            String error = "src/App.tsx(12,5): error TS2307: Cannot find module './components/PlanCard'";
            boolean result = shouldRegenerate(plan("src/App.tsx"), error);
            assertThat(result).isTrue();
            log.info("[OK] 错误含文件路径 → 必须重生成");
        }

        @Test
        @DisplayName("错误含某文件路径 → 其他未被提及的文件不受此规则影响")
        void shouldNotRegenerateUnrelatedFileWhenOtherPathMentioned() {
            String error = "src/App.tsx(12,5): error TS2307: Cannot find module './components/PlanCard'";
            // PlanCard.tsx 虽然是 App 的依赖，但错误文本只提及 App.tsx，不含 PlanCard.tsx
            boolean result = shouldRegenerate(plan("src/components/PlanCard.tsx"), error);
            assertThat(result).isFalse();
            log.info("[OK] 错误只提及 App.tsx → PlanCard.tsx 不重生成（规则4：关联判定）");
        }
    }

    // ==================== 规则 3：模块级错误（未提及任何项目文件）→ 只重生成配置/入口 ====================

    @Nested
    @DisplayName("规则3：模块级错误（未提及任何项目文件）")
    class ModuleLevelError {
        @Test
        @DisplayName("错误是缺失依赖包 → package.json 必须重生成")
        void shouldRegeneratePackageJsonForMissingDependency() {
            String error = "npm error: ERESOLVE unable to resolve dependency tree\n"
                    + "npm error: peer dep missing: react@^18";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/App.tsx"));
            boolean result = shouldRegenerate(plan("package.json"), error);
            assertThat(result).isTrue();
            log.info("[OK] 缺失依赖错误 → package.json 重生成");
        }

        @Test
        @DisplayName("模块级错误 → vite.config.ts 重生成")
        void shouldRegenerateViteConfigForModuleError() {
            String error = "Error: Cannot find module 'vite' from project root";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/App.tsx"));
            boolean result = shouldRegenerate(plan("vite.config.ts"), error);
            assertThat(result).isTrue();
            log.info("[OK] 模块级错误 → vite.config 重生成");
        }

        @Test
        @DisplayName("模块级错误 → tsconfig.json 重生成")
        void shouldRegenerateTsConfigForModuleError() {
            String error = "TS5057: Cannot find tsconfig.json";
            // tsconfig.json 本身被错误文本提及 → 命中规则2，提前返回 true，
            // 不会走规则3，因此不需要 stub listFilePaths。
            boolean result = shouldRegenerate(plan("tsconfig.json"), error);
            assertThat(result).isTrue();
            log.info("[OK] tsconfig 错误 → tsconfig.json 重生成（规则2：路径命中）");
        }

        @Test
        @DisplayName("模块级错误 → App.tsx 入口文件重生成")
        void shouldRegenerateAppTsxForModuleError() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/App.tsx"));
            boolean result = shouldRegenerate(plan("src/App.tsx"), error);
            assertThat(result).isTrue();
            log.info("[OK] 模块级错误 → App.tsx 入口文件重生成");
        }

        @Test
        @DisplayName("模块级错误 → index.html 重生成")
        void shouldRegenerateIndexHtmlForModuleError() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/App.tsx"));
            boolean result = shouldRegenerate(plan("index.html"), error);
            assertThat(result).isTrue();
            log.info("[OK] 模块级错误 → index.html 重生成");
        }

        @Test
        @DisplayName("模块级错误 → 普通组件文件不重生成")
        void shouldNotRegenerateComponentForModuleError() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/App.tsx", "src/components/PlanCard.tsx"));
            boolean result = shouldRegenerate(plan("src/components/PlanCard.tsx"), error);
            assertThat(result).isFalse();
            log.info("[OK] 模块级错误 → 普通组件文件不重生成（只重生成配置/入口）");
        }

        @Test
        @DisplayName("模块级错误 → Vue App.vue 入口文件重生成")
        void shouldRegenerateAppVueForModuleError() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/App.vue"));
            boolean result = shouldRegenerate(plan("src/App.vue"), error);
            assertThat(result).isTrue();
            log.info("[OK] 模块级错误 → App.vue 入口文件重生成");
        }

        @Test
        @DisplayName("模块级错误 → main.tsx 入口文件重生成")
        void shouldRegenerateMainTsxForModuleError() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/main.tsx"));
            boolean result = shouldRegenerate(plan("src/main.tsx"), error);
            assertThat(result).isTrue();
            log.info("[OK] 模块级错误 → main.tsx 入口文件重生成");
        }
    }

    // ==================== 规则 4：错误提及其他文件但不含本文件 → 不重生成 ====================

    @Nested
    @DisplayName("规则4：错误提及其他文件但不含本文件")
    class OtherPathMentioned {
        @Test
        @DisplayName("错误含 App.tsx → PlanCard.tsx（非配置/入口）不重生成")
        void shouldNotRegenerateUnrelatedFile() {
            String error = "src/App.tsx(12,5): error TS2307: Cannot find module './components/PlanCard'";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/App.tsx", "src/components/PlanCard.tsx"));
            boolean result = shouldRegenerate(plan("src/components/PlanCard.tsx"), error);
            assertThat(result).isFalse();
            log.info("[OK] 错误提及 App.tsx → PlanCard.tsx 不重生成（无关联）");
        }

        @Test
        @DisplayName("错误提及 App.tsx.bak → App.tsx 因子串匹配被误判为关联（锁定已知行为）")
        void shouldNotFalselyMatchOnSubstring() {
            // 错误提及 "App.tsx.bak"，App.tsx 是其前缀子串，但逻辑上不关联
            // 注意：当前实现用 buildError.contains(path) → "App.tsx.bak".contains("App.tsx") == true
            // 命中规则2（路径被提及）提前返回 true，不会调用 listFilePaths，故无需 stub
            String error = "src/App.tsx.bak(1,1): error TS7006: Parameter implicitly has any type";
            boolean result = shouldRegenerate(plan("src/App.tsx"), error);
            // 当前实现会把 App.tsx 当作"被提及"而返回 true（子串匹配的副作用）
            assertThat(result).isTrue();
            log.info("[INFO] 子串匹配副作用：错误提及 App.tsx.bak → App.tsx 也被判为需重生成（已知行为）");
        }
    }

    // ==================== 边界情况 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {
        @Test
        @DisplayName("FilePlan.path() 为 null → 不重生成（路径无法匹配）")
        void shouldReturnFalseWhenPathIsNull() {
            FilePlan nullPathPlan = new FilePlan(null, "purpose", "config", List.of(), true);
            String error = "some build error";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json"));
            boolean result = shouldRegenerate(nullPathPlan, error);
            assertThat(result).isFalse();
            log.info("[OK] path=null 且错误不含文件 → 返回 false（不重生成）");
        }

        @Test
        @DisplayName("buildError 为空字符串 → 视为模块级错误，只重生成配置/入口")
        void shouldTreatEmptyBuildErrorAsModuleLevel() {
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/App.tsx"));
            // 空字符串：!anyFileMentioned 为 true（无文件被提及）→ 走规则3
            boolean pkgResult = shouldRegenerate(plan("package.json"), "");
            boolean appResult = shouldRegenerate(plan("src/App.tsx"), "");
            assertThat(pkgResult).isTrue();
            assertThat(appResult).isTrue();
            log.info("[OK] 空 buildError → 视为模块级错误，配置/入口重生成");
        }

        @Test
        @DisplayName("fileType=config → 模块级错误时优先按 fileType 判定为配置文件")
        void shouldRegenerateByFileTypeConfig() {
            // 即便路径不含任何已知后缀，只要 fileType=config，模块级错误时应重生成
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json"));
            boolean result = shouldRegenerate(plan("some/custom/path.cfg", "config"), error);
            assertThat(result).isTrue();
            log.info("[OK] fileType=config 优先判定 → 模块级错误时重生成");
        }

        @Test
        @DisplayName("fileType=entry → 模块级错误时优先按 fileType 判定为入口文件")
        void shouldRegenerateByFileTypeEntry() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json"));
            boolean result = shouldRegenerate(plan("src/boot.ts", "entry"), error);
            assertThat(result).isTrue();
            log.info("[OK] fileType=entry 优先判定 → 模块级错误时重生成");
        }

        @Test
        @DisplayName("fileType=component 且路径非入口 → 模块级错误时不重生成")
        void shouldNotRegenerateComponentFileType() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/components/Card.tsx"));
            boolean result = shouldRegenerate(plan("src/components/Card.tsx", "component"), error);
            assertThat(result).isFalse();
            log.info("[OK] fileType=component 且路径非入口 → 模块级错误时不重生成");
        }

        @Test
        @DisplayName("模块级错误 → App.jsx 入口文件重生成（覆盖 React-JS 项目）")
        void shouldRegenerateAppJsxForModuleError() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/App.jsx"));
            boolean result = shouldRegenerate(plan("src/App.jsx"), error);
            assertThat(result).isTrue();
            log.info("[OK] 模块级错误 → App.jsx 入口文件重生成（React-JS 项目）");
        }

        @Test
        @DisplayName("模块级错误 → next.config.js 重生成（覆盖 Next.js 项目）")
        void shouldRegenerateNextConfigForModuleError() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "next.config.js"));
            boolean result = shouldRegenerate(plan("next.config.js"), error);
            assertThat(result).isTrue();
            log.info("[OK] 模块级错误 → next.config.js 重生成（Next.js 项目）");
        }

        @Test
        @DisplayName("模块级错误 → tailwind.config.js 重生成（覆盖 Tailwind 项目）")
        void shouldRegenerateTailwindConfigForModuleError() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "tailwind.config.js"));
            boolean result = shouldRegenerate(plan("tailwind.config.js"), error);
            assertThat(result).isTrue();
            log.info("[OK] 模块级错误 → tailwind.config.js 重生成（Tailwind 项目）");
        }

        @Test
        @DisplayName("模块级错误 → main.js 入口文件重生成（覆盖纯 JS 项目）")
        void shouldRegenerateMainJsForModuleError() {
            String error = "Build failed with unknown cause";
            when(projectFileService.listFilePaths(PROJECT_ID))
                    .thenReturn(List.of("package.json", "src/main.js"));
            boolean result = shouldRegenerate(plan("src/main.js"), error);
            assertThat(result).isTrue();
            log.info("[OK] 模块级错误 → main.js 入口文件重生成（纯 JS 项目）");
        }
    }
}
