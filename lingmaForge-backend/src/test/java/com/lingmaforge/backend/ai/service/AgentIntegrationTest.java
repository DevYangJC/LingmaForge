package com.lingmaforge.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.service.CodeGenAgent;
import com.lingmaforge.backend.workbench.ai.service.ExecutionPlanner;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;
import com.lingmaforge.backend.workbench.ai.service.RequirementAnalyzer;
import com.lingmaforge.backend.workbench.entity.ProjectFileEntity;
import com.lingmaforge.backend.workbench.mapper.ProjectFileMapper;
import com.lingmaforge.backend.common.model.CreateProjectRequest;
import com.lingmaforge.backend.common.model.PlanResult;
import com.lingmaforge.backend.common.model.RequirementSpec;
import com.lingmaforge.backend.workbench.service.ProjectFileService;
import com.lingmaforge.backend.workbench.service.ProjectService;
import com.lingmaforge.backend.workbench.service.PromptTemplateLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * Agent 真实 AI 调用集成测试。
 *
 * <p>运行条件: LINGMA_INTEGRATION_TEST=true 且至少一个模型的 API Key 已配置。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Agent 真实 AI 集成测试")
class AgentIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AgentIntegrationTest.class);

    @Autowired private AgentFactory agentFactory;
    @Autowired private ProjectService projectService;
    @Autowired private ProjectFileService projectFileService;
    @Autowired private ProjectFileMapper projectFileMapper;
    @Autowired private PromptTemplateLoader promptLoader;

    private Long projectId;

    @BeforeEach
    void setUp() {
        projectService.createProject(
                new CreateProjectRequest("AI测试-" + System.nanoTime(), "集成测试", "react-vite-ts"));
        var projects = projectService.list();
        projectId = projects.get(projects.size() - 1).getId();
        GenerationContext.set(projectId, "test-" + System.nanoTime(), new TestEmitter());
        log.info("================================================================");
        log.info("  Agent 集成测试 - 项目ID: {} (将调用真实AI接口，请确保APIKey已配置)", projectId);
        log.info("================================================================");
    }

    @AfterEach
    void tearDown() {
        GenerationContext.clear();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("测试一：需求分析 Agent")
    class RequirementAnalysis {

        @Test
        @Order(1)
        @EnabledIfEnvironmentVariable(named = "LINGMA_INTEGRATION_TEST", matches = "true")
        @DisplayName("自然语言需求 -> 结构化 RequirementSpec")
        void shouldOutputStructuredRequirement() {
            RequirementAnalyzer analyzer = agentFactory.createRequirementAnalyzer();

            String prompt = "Generate a simple Todo app with a task list page, "
                    + "support adding and deleting tasks, each task has a title and completion status.";

            log.info("================================================================");
            log.info("  测试 1/4: 需求分析 Agent (agent=requirement-analysis, 期望模型=deepseek-flash)");
            log.info("----------------------------------------------------------------");
            log.info("  [AI 输入] prompt ({} 字): {}", prompt.length(), prompt);
            log.info("================================================================");

            Instant start = Instant.now();
            RequirementSpec spec = analyzer.analyze(prompt);
            Duration elapsed = Duration.between(start, Instant.now());

            log.info("----------------------------------------------------------------");
            log.info("  [AI 输出] 耗时: {}ms", elapsed.toMillis());
            log.info("----------------------------------------------------------------");
            logResults(spec);

            assertThat(spec.appName()).isNotBlank();
            assertThat(spec.appName().toLowerCase()).containsAnyOf("todo", "待办", "任务");
            assertThat(spec.pages()).isNotEmpty();
            for (var page : spec.pages()) {
                assertThat(page.name()).isNotBlank();
                assertThat(page.route()).isNotBlank();
                assertThat(page.components()).isNotNull();
            }
            assertThat(spec.apis()).isNotEmpty();
            for (var api : spec.apis()) {
                assertThat(api.name()).isNotBlank();
                assertThat(api.path()).isNotBlank();
                assertThat(List.of("GET","POST","PUT","DELETE","PATCH")).contains(api.method());
            }
            assertThat(spec.features()).isNotEmpty();
            assertThat(spec.features().stream()
                    .anyMatch(f -> {
                        String lower = f.toLowerCase();
                        return lower.contains("添加") || lower.contains("删除")
                                || lower.contains("任务") || lower.contains("完成")
                                || lower.contains("add") || lower.contains("delete")
                                || lower.contains("task") || lower.contains("todo")
                                || lower.contains("done") || lower.contains("新增")
                                || lower.contains("移除") || lower.contains("待办");
                    }))
                    .describedAs("特性列表应包含任务相关操作词")
                    .isTrue();
            assertThat(spec.style()).isNotNull();
            assertThat(spec.style().theme()).isNotBlank();
            assertThat(spec.style().themeName()).isNotBlank();

            log.info("================================================================");
            log.info("  [通过] 测试1/4: 需求分析Agent ({}ms / {}页 / {}API / {}特性 / 主题={})",
                    elapsed.toMillis(), spec.pages().size(), spec.apis().size(),
                    spec.features().size(), spec.style().theme());
            log.info("================================================================");
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("测试二：执行规划 Agent")
    class ExecutionPlanning {

        @Test
        @Order(2)
        @EnabledIfEnvironmentVariable(named = "LINGMA_INTEGRATION_TEST", matches = "true")
        @DisplayName("需求规格 -> 文件清单 (config在component之前)")
        void shouldOutputOrderedFilePlan() {
            String reqPrompt = "Generate a tech blog system with an article list page "
                    + "(cards, category tags), an article detail page, and an About Me page.";

            log.info("================================================================");
            log.info("  测试 2/4: 执行规划 Agent (2轮AI调用: 需求分析 + 执行规划)");
            log.info("----------------------------------------------------------------");
            log.info("  [第1轮 AI输入] prompt ({} 字): {}", reqPrompt.length(), reqPrompt);
            log.info("================================================================");

            Instant start = Instant.now();
            RequirementAnalyzer analyzer = agentFactory.createRequirementAnalyzer();
            RequirementSpec spec = analyzer.analyze(reqPrompt);
            log.info("  [第1轮完成] {}ms, appName='{}', pages={}, apis={}",
                    Duration.between(start, Instant.now()).toMillis(),
                    spec.appName(), spec.pages().size(), spec.apis().size());

            ExecutionPlanner planner = agentFactory.createExecutionPlanner();
            String planPrompt = "请规划文件清单：\n应用: " + spec.appName();

            log.info("  [第2轮 AI输入] prompt: '{}'", planPrompt);
            Instant planStart = Instant.now();
            PlanResult plan = planner.plan(planPrompt);
            Duration planElapsed = Duration.between(planStart, Instant.now());
            Duration totalElapsed = Duration.between(start, Instant.now());

            log.info("  [第2轮完成] {}ms, 总计 {}ms", planElapsed.toMillis(), totalElapsed.toMillis());
            log.info("----------------------------------------------------------------");
            log.info("  [AI 输出] {} 个文件 / framework={} / pkg={}",
                    plan.files().size(), plan.framework(), plan.packageManager());
            for (int i = 0; i < plan.files().size(); i++) {
                var f = plan.files().get(i);
                log.info("    #{}. [{}] {} -- {} (依赖: {})",
                        i + 1, f.fileType(), f.path(), f.purpose(), f.dependencies());
            }
            log.info("  生成顺序: {}, 构建命令: {}", plan.generationOrder(), plan.buildCommands());

            assertThat(plan.framework()).isEqualTo("react-vite-ts");
            assertThat(plan.files().size()).isGreaterThanOrEqualTo(6);
            int lastConfig = lastIndexByType(plan, "config");
            int firstComp = firstIndexByType(plan, "component");
            if (lastConfig >= 0 && firstComp >= 0) assertThat(lastConfig).isLessThan(firstComp);
            int lastEntry = lastIndexByType(plan, "entry");
            int lastNonEntry = lastIndexOfNonType(plan, "entry");
            if (lastEntry >= 0 && lastNonEntry >= 0) assertThat(lastEntry).isGreaterThan(lastNonEntry);
            assertThat(plan.generationOrder()).hasSize(plan.files().size());

            log.info("================================================================");
            log.info("  [通过] 测试2/4: 执行规划Agent (2轮共{}ms / {}文件 / 排序正确)",
                    totalElapsed.toMillis(), plan.files().size());
            log.info("================================================================");
        }

        private int lastIndexByType(PlanResult p, String t) {
            for (int i = p.files().size()-1; i >= 0; i--) if (t.equals(p.files().get(i).fileType())) return i;
            return -1;
        }
        private int firstIndexByType(PlanResult p, String t) {
            for (int i = 0; i < p.files().size(); i++) if (t.equals(p.files().get(i).fileType())) return i;
            return -1;
        }
        private int lastIndexOfNonType(PlanResult p, String t) {
            for (int i = p.files().size()-1; i >= 0; i--) if (!t.equals(p.files().get(i).fileType())) return i;
            return -1;
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("测试三：代码生成 Agent")
    class CodeGeneration {

        @Test
        @Order(3)
        @EnabledIfEnvironmentVariable(named = "LINGMA_INTEGRATION_TEST", matches = "true")
        @DisplayName("代码生成Agent调用writeFile落盘，代码无markdown污染")
        void shouldGenerateCleanCode() {
            CodeGenAgent agent = agentFactory.createCodeGenAgent();
            String userPrompt = promptLoader.loadUserPrompt("code-generation",
                    java.util.Map.of(
                            "appName", "Todo 应用", "description", "一个简单任务管理应用",
                            "filePath", "src/App.tsx", "fileType", "entry",
                            "fileDescription", "应用入口文件，定义路由",
                            "fileContext", "无", "analysisResult", "{}", "buildError", "无"));

            log.info("================================================================");
            log.info("  测试 3/4: 代码生成 Agent (agent=code-generation, 带@Tool:writeFile)");
            log.info("  期望模型=deepseek-pro, 最大工具调用轮数=12");
            log.info("----------------------------------------------------------------");
            log.info("  [AI 输入] prompt已从模板加载 ({} 字), 变量: appName=Todo应用, filePath=src/App.tsx",
                    userPrompt.length());
            log.info("================================================================");

            Instant start = Instant.now();
            String result = agent.generate(userPrompt);
            Duration elapsed = Duration.between(start, Instant.now());

            var allFiles = projectFileMapper.selectList(
                    new LambdaQueryWrapper<ProjectFileEntity>()
                            .eq(ProjectFileEntity::getProjectId, projectId));
            log.info("----------------------------------------------------------------");
            log.info("  [AI 输出] 耗时: {}ms / Agent文本回复: \"{}\"", elapsed.toMillis(), result);
            log.info("  工具调用后项目文件数: {} 个", allFiles.size());
            allFiles.forEach(f -> log.info("    - {} ({}字节, status={})",
                    f.getPath(), f.getContent() != null ? f.getContent().length() : 0, f.getStatus()));

            assertThat(result).isNotNull();
            var appFiles = projectFileMapper.selectList(
                    new LambdaQueryWrapper<ProjectFileEntity>()
                            .eq(ProjectFileEntity::getProjectId, projectId)
                            .eq(ProjectFileEntity::getPath, "src/App.tsx"));

            if (!appFiles.isEmpty()) {
                String diskContent = appFiles.get(0).getContent();
                int lineCount = diskContent != null ? (int) diskContent.lines().count() : 0;
                log.info("  磁盘 src/App.tsx: {} 行", lineCount);
                if (diskContent != null) diskContent.lines().limit(5).forEach(l -> log.info("    {}", l));

                if (diskContent != null && !diskContent.isBlank()) {
                    String firstLine = diskContent.stripLeading().lines().findFirst().orElse("");
                    assertThat(firstLine).describedAs("第一行不应以```开头").doesNotStartWith("```");
                    assertThat(diskContent).describedAs("代码不应被中文客套话污染")
                            .doesNotContain("以下是代码", "好的，下面是", "让我为您生成");
                    assertThat(diskContent).contains("import");
                    assertThat(diskContent).contains("export");

                    log.info("================================================================");
                    log.info("  [通过] 测试3/4: 代码生成Agent ({}ms / {}行 / markdown:无 / 客套话:无)",
                            elapsed.toMillis(), lineCount);
                    log.info("================================================================");
                }
            } else {
                log.warn("磁盘未找到 src/App.tsx —— 大模型可能未调用 writeFile 工具!");
            }
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("测试四：迭代修改 Agent")
    class IterationModification {

        @Test
        @Order(4)
        @EnabledIfEnvironmentVariable(named = "LINGMA_INTEGRATION_TEST", matches = "true")
        @DisplayName("searchCode搜索 + patchFile增量修改")
        void shouldSearchAndPatch() {
            String original = "import React from 'react';\n"
                    + "const Header = () => {\n"
                    + "  return <header style={{background: '#333', color: '#fff'}}>\n"
                    + "    <h1>My App</h1>\n"
                    + "  </header>;\n"
                    + "};\n"
                    + "export default Header;\n";
            projectFileService.writeFile(projectId, "src/components/Header.tsx", original, "new");

            IterationAgent agent = agentFactory.createIterationAgent();
            String iterPrompt = "The project has src/components/Header.tsx. "
                    + "Use searchCode to find 'background', then change '#333' to '#1e40af' "
                    + "via patchFile for an incremental modification.";

            log.info("================================================================");
            log.info("  测试 4/4: 迭代修改 Agent (agent=iteration-modification, @Tool:searchCode/patchFile)");
            log.info("  期望模型=deepseek-pro, 最大工具调用轮数=12");
            log.info("----------------------------------------------------------------");
            log.info("  原始文件: src/components/Header.tsx ({} 行)", original.lines().count());
            log.info("  [AI 输入] prompt ({} 字): {}", iterPrompt.length(), iterPrompt);
            log.info("================================================================");

            log.info("  [修改前] src/components/Header.tsx:");
            original.lines().forEach(l -> log.info("    {}", l));

            Instant start = Instant.now();
            String result = agent.modify(iterPrompt);
            Duration elapsed = Duration.between(start, Instant.now());

            String modified = projectFileService.readFile(projectId, "src/components/Header.tsx");
            log.info("  [修改后] src/components/Header.tsx:");
            if (modified != null) modified.lines().forEach(l -> log.info("    {}", l));
            else log.warn("    (文件不存在)");

            log.info("================================================================");
            log.info("  [通过] 测试4/4: 迭代修改Agent ({}ms / agent回复: {})",
                    elapsed.toMillis(), result);
            log.info("================================================================");

            assertThat(result).isNotNull();
        }
    }

    private void logResults(RequirementSpec spec) {
        log.info("  --- 需求分析结果 ---");
        log.info("  应用名: {}", spec.appName());
        log.info("  描述: {}", spec.description());
        log.info("  页面 ({}):", spec.pages().size());
        spec.pages().forEach(p -> log.info("    {} -> {}  组件: {}", p.name(), p.route(), p.components()));
        log.info("  API ({}):", spec.apis().size());
        spec.apis().forEach(a -> log.info("    {} {} -- {}", a.method(), a.path(), a.name()));
        log.info("  特性 ({}): {}", spec.features().size(), spec.features());
        log.info("  主题: {} ({})",
                spec.style() != null ? spec.style().theme() : "null",
                spec.style() != null ? spec.style().themeName() : "");
    }

    private static class TestEmitter implements GenerationStreamEmitter {
        @Override public void emitNode(String n, String t, String tt) {
            log.info("[emitter] 节点推送: {} -> {}", n, t);
        }
        @Override public void emitFile(String p, String c, String s) {
            log.info("[emitter] 文件推送: {} ({}字节, status={})", p, c != null ? c.length() : 0, s);
        }
        @Override public void emitLog(String t) {
            log.info("[emitter] 日志: {}", t);
        }
        @Override public void complete(String u, Integer po, Integer bt) {
            log.info("[emitter] 完成: url={} port={} time={}s", u, po, bt);
        }
        @Override public void error(String m) {
            log.error("[emitter] 错误: {}", m);
        }
    }
}
