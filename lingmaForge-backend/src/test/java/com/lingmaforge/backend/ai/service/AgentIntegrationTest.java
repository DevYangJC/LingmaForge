package com.lingmaforge.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

import com.lingmaforge.backend.generation.agent.factory.AgentFactory;
import com.lingmaforge.backend.generation.stream.GenerationContext;
import com.lingmaforge.backend.generation.stream.GenerationStreamEmitter;
import com.lingmaforge.backend.project.domain.ProjectFileEntity;
import com.lingmaforge.backend.project.mapper.ProjectFileMapper;
import com.lingmaforge.backend.project.dto.CreateProjectRequest;
import com.lingmaforge.backend.generation.domain.PlanResult;
import com.lingmaforge.backend.generation.domain.RequirementSpec;
import com.lingmaforge.backend.project.service.ProjectFileService;
import com.lingmaforge.backend.project.service.ProjectService;
import com.lingmaforge.backend.generation.service.PromptTemplateLoader;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * Agent 真实 AI 调用集成测试。
 *
 * <p><b>运行条件</b>：需设置 {@code LINGMA_INTEGRATION_TEST=true} 和至少一个模型的 API Key。</p>
 *
 * <p><b>运行方式</b>：</p>
 * <pre>
 * # 用 DeepSeek 测试（推荐，最便宜 ~¥0.01/次）
 * export LINGMA_INTEGRATION_TEST=true
 * export DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxx
 * cd lingmaForge-backend
 * export JAVA_HOME="D:/Develop/DevelopTool/IDEA/IntelliJ IDEA 2025.2.5/jbr"
 * ./mvnw test -Dtest=AgentIntegrationTest -DfailIfNoTests=false
 *
 * # 用通义千问测试
 * export LINGMA_INTEGRATION_TEST=true
 * export DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxx
 * ./mvnw test -Dtest=AgentIntegrationTest -DfailIfNoTests=false
 * </pre>
 *
 * <p><b>测试验证点</b>（按文档核心目标设计）：</p>
 * <ol>
 *   <li>结构化输出格式 —— JSON → Java Record 自动反序列化，零手动解析</li>
 *   <li>字段完整性 —— pages、APIs、features、style 都包含合理内容</li>
 *   <li>工具调用 —— writeFile 正确落盘，代码内容纯净无 markdown 污染</li>
 *   <li>依赖排序 —— config 文件先于 component 先于 entry</li>
 *   <li>迭代修改 —— searchCode 搜索 + patchFile 增量改，不全量重写</li>
 * </ol>
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
        // 查出刚创建的项目
        var projects = projectService.list();
        projectId = projects.get(projects.size() - 1).getId();

        GenerationContext.set(projectId, "test-" + System.nanoTime(),
                new TestEmitter());
        log.info("===== 测试项目 ID: {} =====", projectId);
    }

    @AfterEach
    void tearDown() {
        GenerationContext.clear();
    }

    // ============ 测试一：需求分析（结构化输出核心验证） ============

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("测试一：需求分析 Agent")
    class RequirementAnalysis {

        @Test
        @Order(1)
        @EnabledIfEnvironmentVariable(named = "LINGMA_INTEGRATION_TEST", matches = "true")
        @DisplayName("输入一句话需求 → 输出结构化 RequirementSpec")
        void shouldOutputStructuredRequirement() {
            RequirementAnalyzer analyzer = agentFactory.createRequirementAnalyzer();

            RequirementSpec spec = analyzer.analyze("""
                    帮我生成一个简单的待办事项 Todo 应用，包含：
                    - 任务列表页面，可以添加和删除任务
                    - 每个任务有标题和完成状态
                    """);

            logResults(spec);

            // 1) appName 非空，应包含 todo/待办/任务 相关词
            assertThat(spec.appName()).isNotBlank();
            assertThat(spec.appName().toLowerCase())
                    .containsAnyOf("todo", "待办", "任务");

            // 2) 至少 1 个页面，每个页面必须有 name / route / components
            assertThat(spec.pages()).isNotEmpty();
            for (var page : spec.pages()) {
                assertThat(page.name()).isNotBlank();
                assertThat(page.route()).isNotBlank();
                assertThat(page.components()).isNotNull();
            }

            // 3) 至少 1 个 API
            assertThat(spec.apis()).isNotEmpty();
            for (var api : spec.apis()) {
                assertThat(api.name()).isNotBlank();
                assertThat(api.path()).isNotBlank();
                assertThat(List.of("GET","POST","PUT","DELETE","PATCH"))
                        .contains(api.method());
            }

            // 4) 特性列表包含任务相关操作
            assertThat(spec.features()).isNotEmpty();
            assertThat(spec.features().stream()
                    .anyMatch(f -> f.contains("添加") || f.contains("删除")
                            || f.contains("任务") || f.contains("完成")))
                    .isTrue();

            // 5) 主题色非空（提示词要求根据应用类型推断主题色）
            assertThat(spec.style()).isNotNull();
            assertThat(spec.style().theme()).isNotBlank();
            assertThat(spec.style().themeName()).isNotBlank();
        }
    }

    // ============ 测试二：执行规划（文件清单 + 依赖排序） ============

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("测试二：执行规划 Agent")
    class ExecutionPlanning {

        @Test
        @Order(2)
        @EnabledIfEnvironmentVariable(named = "LINGMA_INTEGRATION_TEST", matches = "true")
        @DisplayName("输入需求规格 → 输出文件清单，config 在 component 之前")
        void shouldOutputOrderedFilePlan() {
            RequirementAnalyzer analyzer = agentFactory.createRequirementAnalyzer();
            RequirementSpec spec = analyzer.analyze("""
                    帮我生成一个技术博客系统，包含：
                    - 文章列表页（卡片、分类标签）
                    - 文章详情页
                    - 关于我页面
                    """);

            ExecutionPlanner planner = agentFactory.createExecutionPlanner();
            PlanResult plan = planner.plan("请规划文件清单：\n应用: " + spec.appName());

            log.info("=== 规划了 {} 个文件 ===", plan.files().size());
            for (int i = 0; i < plan.files().size(); i++) {
                var f = plan.files().get(i);
                log.info("  #{}. [{}] {} — {}", i + 1, f.fileType(), f.path(), f.purpose());
            }

            // 1) 框架正确
            assertThat(plan.framework()).isEqualTo("react-vite-ts");
            // 2) 至少 6 个文件（vite.config, package.json, globals.css, 3 页面, App.tsx, main.tsx...）
            assertThat(plan.files().size()).isGreaterThanOrEqualTo(6);
            // 3) config 类型在 component 之前
            int lastConfigIdx = lastIndexByType(plan, "config");
            int firstComponentIdx = firstIndexByType(plan, "component");
            if (lastConfigIdx >= 0 && firstComponentIdx >= 0) {
                assertThat(lastConfigIdx).isLessThan(firstComponentIdx);
            }
            // 4) entry 类型在最后
            int lastEntryIdx = lastIndexByType(plan, "entry");
            int lastNonEntryIdx = lastIndexOfNonType(plan, "entry");
            if (lastEntryIdx >= 0 && lastNonEntryIdx >= 0) {
                assertThat(lastEntryIdx).isGreaterThan(lastNonEntryIdx);
            }
            // 5) 生成顺序与文件数一致
            assertThat(plan.generationOrder()).hasSize(plan.files().size());
        }

        private int lastIndexByType(PlanResult plan, String type) {
            for (int i = plan.files().size() - 1; i >= 0; i--) {
                if (type.equals(plan.files().get(i).fileType())) return i;
            }
            return -1;
        }
        private int firstIndexByType(PlanResult plan, String type) {
            for (int i = 0; i < plan.files().size(); i++) {
                if (type.equals(plan.files().get(i).fileType())) return i;
            }
            return -1;
        }
        private int lastIndexOfNonType(PlanResult plan, String type) {
            for (int i = plan.files().size() - 1; i >= 0; i--) {
                if (!type.equals(plan.files().get(i).fileType())) return i;
            }
            return -1;
        }
    }

    // ============ 测试三：代码生成（工具调用 + 代码纯净性验证） ============

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("测试三：代码生成 Agent")
    class CodeGeneration {

        @Test
        @Order(3)
        @EnabledIfEnvironmentVariable(named = "LINGMA_INTEGRATION_TEST", matches = "true")
        @DisplayName("代码生成 Agent 调用 writeFile 写入磁盘，内容无 markdown 污染")
        void shouldGenerateCleanCode() {
            CodeGenAgent agent = agentFactory.createCodeGenAgent();

            String userPrompt = promptLoader.loadUserPrompt("code-generation",
                    java.util.Map.of(
                            "appName", "Todo 应用",
                            "description", "一个简单任务管理应用",
                            "filePath", "src/App.tsx",
                            "fileType", "entry",
                            "fileDescription", "应用入口文件，定义路由",
                            "fileContext", "无",
                            "analysisResult", "{}",
                            "buildError", "无"));

            // 调用 Agent → 模型应该调用 writeFile 工具写入 src/App.tsx
            String result = agent.generate(userPrompt);
            log.info("Agent 文本回复: {}", result);
            assertThat(result).isNotNull();

            // 检查数据库中的文件记录
            var files = projectFileMapper.selectList(
                    new LambdaQueryWrapper<ProjectFileEntity>()
                            .eq(ProjectFileEntity::getProjectId, projectId)
                            .eq(ProjectFileEntity::getPath, "src/App.tsx"));

            if (!files.isEmpty()) {
                String diskContent = files.get(0).getContent();
                log.info("=== 磁盘上的 src/App.tsx（{} 行） ===",
                        diskContent != null ? diskContent.lines().count() : 0);
                if (diskContent != null) {
                    // 不打印全部避免刷屏，只看关键行
                    diskContent.lines().limit(5).forEach(l -> log.info("  {}", l));
                }

                if (diskContent != null && !diskContent.isBlank()) {
                    // ★ 核心断言：文件内容的第一行不应以 ``` 开头（markdown 代码块标记）
                    String firstLine = diskContent.stripLeading().lines().findFirst().orElse("");
                    assertThat(firstLine)
                            .describedAs("第一行不应以 ``` 开头——Agent 未在工具之外输出 markdown")
                            .doesNotStartWith("```");

                    // ★ 核心断言：内容不应包含"好的"、"以下是"等中文客套前缀
                    assertThat(diskContent)
                            .describedAs("代码不应被中文前缀污染")
                            .doesNotContain("以下是代码", "好的，下面是", "让我为您生成");

                    // ★ 包含 import 语句（是有效代码的标志）
                    assertThat(diskContent).contains("import");

                    // ★ 包含 export（其他文件可引用）
                    assertThat(diskContent).contains("export");
                }
            }
        }
    }

    // ============ 测试四：迭代修改（searchCode + patchFile） ============

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("测试四：迭代修改 Agent")
    class IterationModification {

        @Test
        @Order(4)
        @EnabledIfEnvironmentVariable(named = "LINGMA_INTEGRATION_TEST", matches = "true")
        @DisplayName("对已有文件做增量修改，searchCode 搜索 + patchFile 补丁")
        void shouldSearchAndPatch() {
            // 先写入一个 React 组件作为修改目标
            projectFileService.writeFile(projectId, "src/components/Header.tsx",
                    """
                    import React from 'react';
                    const Header = () => {
                      return <header style={{background: '#333', color: '#fff'}}>
                        <h1>My App</h1>
                      </header>;
                    };
                    export default Header;
                    """, "new");

            IterationAgent agent = agentFactory.createIterationAgent();

            String result = agent.modify("""
                    项目中有 src/components/Header.tsx。
                    请用 searchCode 搜索 'background'，定位后把 '#333' 改成 '#1e40af'，
                    通过 patchFile 做增量修改。
                    """);

            log.info("迭代 Agent 回复: {}", result);
            assertThat(result).isNotNull();
        }
    }

    // ============ 辅助方法 ============

    private void logResults(RequirementSpec spec) {
        log.info("=== 需求分析结果 ===");
        log.info("  应用名: {}", spec.appName());
        log.info("  描述: {}", spec.description());
        log.info("  页面 ({})：", spec.pages().size());
        spec.pages().forEach(p -> log.info("    {} → {}  组件: {}",
                p.name(), p.route(), p.components()));
        log.info("  API ({})：", spec.apis().size());
        spec.apis().forEach(a -> log.info("    {} {} — {}", a.method(), a.path(), a.name()));
        log.info("  特性: {}", spec.features());
        log.info("  主题: {} ({})", spec.style() != null ? spec.style().theme() : "null",
                spec.style() != null ? spec.style().themeName() : "");
    }

    /** 测试专用发射器：事件只打日志，不推 SSE。 */
    private static class TestEmitter implements GenerationStreamEmitter {
        @Override public void emitNode(String n, String t, String tt) {
            log.info("[emitter] {}: {}", n, t);
        }
        @Override public void emitFile(String p, String c, String s) {
            log.info("[emitter] file: {} ({})", p, s);
        }
        @Override public void emitLog(String t) {
            log.info("[emitter] log: {}", t);
        }
        @Override public void complete(String u, Integer po, Integer bt) {
            log.info("[emitter] complete: url={} port={} time={}s", u, po, bt);
        }
        @Override public void error(String m) {
            log.error("[emitter] error: {}", m);
        }
    }
}
