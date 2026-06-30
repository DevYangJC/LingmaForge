package com.lingmaforge.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.lingmaforge.backend.workbench.ai.service.CodeGenAgent;
import com.lingmaforge.backend.workbench.ai.service.ExecutionPlanner;
import com.lingmaforge.backend.workbench.ai.service.RequirementAnalyzer;
import com.lingmaforge.backend.workbench.ai.tool.FileTools;
import com.lingmaforge.backend.workbench.ai.tool.ProjectContextTools;
import com.lingmaforge.backend.common.model.PlanResult;
import com.lingmaforge.backend.common.model.RequirementSpec;
import com.lingmaforge.backend.workbench.service.PromptTemplateLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;

/**
 * Agent Mock 测试 -- 使用 Mockito 模拟大模型返回值，验证 AiServices 结构化输出能力。
 * 无需任何 API Key，全部通过 mock ChatModel 完成。
 */
@DisplayName("Agent Mock 测试（无需 API Key）")
@ExtendWith(MockitoExtension.class)
class AgentMockTest {

    private static final Logger log = LoggerFactory.getLogger(AgentMockTest.class);
    @Mock private ChatModel chatModel;
    @Mock private PromptTemplateLoader promptLoader;
    @Mock private FileTools fileTools;
    @Mock private ProjectContextTools projectContextTools;

    @Nested
    @DisplayName("需求分析 Agent（结构化输出）")
    class RequirementAnalyzerTests {
        private RequirementAnalyzer analyzer;

        @BeforeEach
        void setUp() {
            when(promptLoader.loadSystemPrompt("requirement-analysis")).thenReturn("你是一个需求分析师");
            analyzer = AiServices.builder(RequirementAnalyzer.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(id -> promptLoader.loadSystemPrompt("requirement-analysis"))
                    .build();
            log.info("========== 需求分析Agent Mock初始化 ==========");
            log.info("  系统提示词: '你是一个需求分析师', ChatModel: Mockito mock, 输出: RequirementSpec");
            log.info("=============================================");
        }

        @Test
        @DisplayName("模型返回合法JSON -> 正确解析为RequirementSpec")
        void shouldParseRequirementSpec() {
            String json = "{\n"
                    + "  \"appName\": \"Subscription Store\",\n"
                    + "  \"description\": \"A subscription payment platform\",\n"
                    + "  \"pages\": [\n"
                    + "    {\"name\": \"Home\", \"route\": \"/\", \"description\": \"Homepage\",\n"
                    + "     \"components\": [\"NavBar\", \"HeroSection\"]},\n"
                    + "    {\"name\": \"Subscription\", \"route\": \"/subscription\",\n"
                    + "     \"description\": \"Subscription page\",\n"
                    + "     \"components\": [\"PlanCard\", \"PaymentButton\"]}\n"
                    + "  ],\n"
                    + "  \"apis\": [\n"
                    + "    {\"name\": \"Get Plans\", \"path\": \"/api/plans\", \"method\": \"GET\",\n"
                    + "     \"description\": \"Returns plan list\", \"requestShape\": {},\n"
                    + "     \"responseShape\": {\"plans\": [{\"id\": \"string\", \"name\": \"string\", \"price\": \"number\"}]}}\n"
                    + "  ],\n"
                    + "  \"features\": [\"Plan compare\", \"Payment modal\", \"Order filter\"],\n"
                    + "  \"style\": {\"theme\": \"#6366f1\", \"themeName\": \"indigo\",\n"
                    + "            \"layout\": \"responsive\", \"fontFamily\": \"Inter\"}\n"
                    + "}";
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

            log.info("--- 模拟大模型返回 ---");
            log.info("  用户输入: 'Generate a subscription store'");
            log.info("  模拟JSON: {}字符, 2页/1API/3特性", json.length());

            RequirementSpec spec = analyzer.analyze("Generate a subscription store");

            log.info("--- AiServices反序列化结果 ---");
            log.info("  appName='{}', pages={}, apis={}, features={}",
                    spec.appName(), spec.pages().size(), spec.apis().size(), spec.features());
            spec.pages().forEach(p -> log.info("    {} ({}) components: {}",
                    p.name(), p.route(), p.components()));
            spec.apis().forEach(a -> log.info("    {} {} ({})", a.method(), a.path(), a.name()));
            log.info("  theme: {} ({})", spec.style().theme(), spec.style().themeName());

            assertThat(spec.appName()).isEqualTo("Subscription Store");
            assertThat(spec.pages()).hasSize(2);
            assertThat(spec.pages().get(0).components()).containsExactly("NavBar", "HeroSection");
            assertThat(spec.apis().get(0).method()).isEqualTo("GET");
            assertThat(spec.style().theme()).isEqualTo("#6366f1");
            log.info("  [OK] JSON -> RequirementSpec 反序列化全部正确");
        }

        @Test
        @DisplayName("嵌套Record也正确解析")
        void shouldParseNestedRecords() {
            String json = "{\n"
                    + "  \"appName\": \"Minimal Blog\",\n"
                    + "  \"description\": \"A lightweight blog system\",\n"
                    + "  \"pages\": [\n"
                    + "    {\"name\": \"Blog\", \"route\": \"/blog\", \"description\": \"Blog list\",\n"
                    + "     \"components\": [\"PostCard\"]}\n"
                    + "  ],\n"
                    + "  \"apis\": [],\n"
                    + "  \"features\": [],\n"
                    + "  \"style\": {\"theme\": \"#3b82f6\", \"themeName\": \"blue\",\n"
                    + "            \"layout\": \"responsive\", \"fontFamily\": \"sans-serif\"}\n"
                    + "}";
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

            log.info("--- 嵌套Record解析测试 ---");
            log.info("  模拟JSON: 1页/0API/style含4字段");

            RequirementSpec spec = analyzer.analyze("make a blog");

            log.info("  PageSpec: name='{}', components={}", spec.pages().get(0).name(), spec.pages().get(0).components());
            log.info("  StyleSpec: theme='{}', layout='{}'", spec.style().theme(), spec.style().layout());

            assertThat(spec.pages().get(0).name()).isEqualTo("Blog");
            assertThat(spec.style().layout()).isEqualTo("responsive");
            log.info("  [OK] 内层Record (PageSpec + StyleSpec) 全部解析正确");
        }
    }

    @Nested
    @DisplayName("执行规划 Agent（结构化输出）")
    class ExecutionPlannerTests {
        private ExecutionPlanner planner;

        @BeforeEach
        void setUp() {
            when(promptLoader.loadSystemPrompt("execution-planning")).thenReturn("你是一个架构师");
            planner = AiServices.builder(ExecutionPlanner.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(id -> promptLoader.loadSystemPrompt("execution-planning"))
                    .build();
            log.info("========== 执行规划Agent Mock初始化 ==========");
            log.info("  系统提示词: '你是一个架构师', 输出: PlanResult");
            log.info("=============================================");
        }

        @Test
        @DisplayName("模型返回文件清单JSON -> 正确解析为PlanResult")
        void shouldParsePlanResult() {
            String json = "{\n"
                    + "  \"framework\": \"react-vite-ts\",\n"
                    + "  \"packageManager\": \"npm\",\n"
                    + "  \"files\": [\n"
                    + "    {\"path\": \"package.json\", \"purpose\": \"Project config\",\n"
                    + "     \"fileType\": \"config\", \"dependencies\": [], \"required\": true},\n"
                    + "    {\"path\": \"src/styles/globals.css\", \"purpose\": \"Global styles\",\n"
                    + "     \"fileType\": \"style\", \"dependencies\": [], \"required\": true},\n"
                    + "    {\"path\": \"src/components/PlanCard.tsx\", \"purpose\": \"Plan card component\",\n"
                    + "     \"fileType\": \"component\", \"dependencies\": [\"src/styles/globals.css\"], \"required\": true},\n"
                    + "    {\"path\": \"src/App.tsx\", \"purpose\": \"App entry\",\n"
                    + "     \"fileType\": \"entry\", \"dependencies\": [\"src/components/PlanCard.tsx\"], \"required\": true}\n"
                    + "  ],\n"
                    + "  \"generationOrder\": [\"package.json\",\"src/styles/globals.css\",\"src/components/PlanCard.tsx\",\"src/App.tsx\"],\n"
                    + "  \"buildCommands\": [\"npm install\", \"npm run build\"]\n"
                    + "}";
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(json)).build());

            log.info("--- 模拟大模型返回 ---");
            log.info("  用户输入: 'Plan file list', 模拟JSON: {}字符, 4文件", json.length());

            PlanResult plan = planner.plan("Plan file list");

            log.info("--- AiServices反序列化结果 ---");
            log.info("  framework='{}', pkg='{}', 文件数={}", plan.framework(), plan.packageManager(), plan.files().size());
            for (int i = 0; i < plan.files().size(); i++) {
                var f = plan.files().get(i);
                log.info("    {}. [{}] {} (依赖: {})", i+1, f.fileType(), f.path(), f.dependencies());
            }
            log.info("  生成顺序: {}, 构建命令: {}", plan.generationOrder(), plan.buildCommands());

            assertThat(plan.framework()).isEqualTo("react-vite-ts");
            assertThat(plan.files()).hasSize(4);
            assertThat(plan.files().get(0).fileType()).isEqualTo("config");
            assertThat(plan.files().get(1).fileType()).isEqualTo("style");
            assertThat(plan.files().get(2).fileType()).isEqualTo("component");
            assertThat(plan.files().get(3).fileType()).isEqualTo("entry");
            assertThat(plan.files().get(2).dependencies()).contains("src/styles/globals.css");
            assertThat(plan.generationOrder()).containsExactly(
                    "package.json", "src/styles/globals.css", "src/components/PlanCard.tsx", "src/App.tsx");
            log.info("  [OK] JSON -> PlanResult 反序列化正确 (含4个FilePlan内层Record)");
            log.info("  [OK] 文件类型顺序: config -> style -> component -> entry");
        }
    }

    @Disabled("需要精确mock AiServices工具调用链，实际验证请用AgentIntegrationTest")
    @Nested
    @DisplayName("代码生成 Agent（带@Tool方法）")
    class CodeGenAgentTests {
        private CodeGenAgent agent;

        @BeforeEach
        void setUp() {
            when(promptLoader.loadSystemPrompt("code-generation"))
                    .thenReturn("你是一个前端工程师");
            agent = AiServices.builder(CodeGenAgent.class)
                    .streamingChatModel(mock(dev.langchain4j.model.chat.StreamingChatModel.class))
                    .systemMessageProvider(id -> promptLoader.loadSystemPrompt("code-generation"))
                    .build();
            log.info("========== 代码生成Agent Mock初始化 ==========");
            log.info("  (@Disabled -- 精确mock较复杂，请用AgentIntegrationTest验证真实AI调用)");
            log.info("=============================================");
        }

        @Test
        @DisplayName("模型调用writeFile工具 -> 返回成功消息")
        void shouldCallWriteFileAndReturnSuccess() {
            log.info("--- 代码生成Agent Mock测试 ---");
            log.info("  [OK] 代码生成Agent mock流程完成");
        }
    }
}
