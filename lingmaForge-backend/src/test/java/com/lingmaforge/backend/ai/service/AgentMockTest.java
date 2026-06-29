package com.lingmaforge.backend.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lingmaforge.backend.ai.tool.FileTools;
import com.lingmaforge.backend.ai.tool.ProjectContextTools;
import com.lingmaforge.backend.model.PlanResult;
import com.lingmaforge.backend.model.RequirementSpec;
import com.lingmaforge.backend.service.PromptTemplateLoader;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;

/**
 * Agent Mock 测试 —— 使用 Mockito 模拟大模型返回值，验证 AiServices 的结构化输出能力。
 *
 * <p><b>测试目标</b>：
 * <ol>
 *   <li>大模型返回合法 JSON 时，AiServices 能正确反序列化为 Java Record</li>
 *   <li>大模型返回的 JSON 字段映射到 Java 字段无误</li>
 *   <li>代码生成 Agent 能正确调用 @Tool 方法</li>
 * </ol>
 *
 * <p><b>无需任何 API Key</b>，全部通过 mock ChatModel 完成。</p>
 */
@DisplayName("Agent Mock 测试（无需 API Key）")
@ExtendWith(MockitoExtension.class)
class AgentMockTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private PromptTemplateLoader promptLoader;

    @Mock
    private FileTools fileTools;

    @Mock
    private ProjectContextTools projectContextTools;

    // ==================== 需求分析 Agent ====================

    @Nested
    @DisplayName("需求分析 Agent（结构化输出）")
    class RequirementAnalyzerTests {

        private RequirementAnalyzer analyzer;

        @BeforeEach
        void setUp() {
            when(promptLoader.loadSystemPrompt("requirement-analysis"))
                    .thenReturn("你是一个需求分析师");

            analyzer = AiServices.builder(RequirementAnalyzer.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(id -> promptLoader.loadSystemPrompt("requirement-analysis"))
                    .build();
        }

        @Test
        @DisplayName("模型返回合法 JSON 时，应正确解析为 RequirementSpec")
        void shouldParseRequirementSpec() {
            // 模拟大模型返回的 JSON
            String jsonResponse = """
                    {
                      "appName": "会员订阅商城",
                      "description": "一个面向C端用户的订阅付费平台",
                      "pages": [
                        {
                          "name": "Home",
                          "route": "/",
                          "description": "首页",
                          "components": ["NavBar", "HeroSection"]
                        },
                        {
                          "name": "Subscription",
                          "route": "/subscription",
                          "description": "订阅页",
                          "components": ["PlanCard", "PaymentButton"]
                        }
                      ],
                      "apis": [
                        {
                          "name": "获取套餐列表",
                          "path": "/api/plans",
                          "method": "GET",
                          "description": "返回套餐列表",
                          "requestShape": {},
                          "responseShape": {"plans": [{"id": "string", "name": "string", "price": "number"}]}
                        }
                      ],
                      "features": ["套餐对比", "支付弹窗", "订单筛选"],
                      "style": {
                        "theme": "#6366f1",
                        "themeName": "indigo",
                        "layout": "responsive",
                        "fontFamily": "Inter, system-ui, sans-serif"
                      }
                    }
                    """;

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(jsonResponse))
                            .build());

            // 调用 → 框架自动反序列化
            RequirementSpec spec = analyzer.analyze("帮我生成一个会员订阅商城");

            // 验证每个字段
            assertThat(spec.appName()).isEqualTo("会员订阅商城");
            assertThat(spec.description()).contains("C端用户");
            assertThat(spec.pages()).hasSize(2);
            assertThat(spec.pages().get(0).name()).isEqualTo("Home");
            assertThat(spec.pages().get(0).route()).isEqualTo("/");
            assertThat(spec.pages().get(0).components()).containsExactly("NavBar", "HeroSection");
            assertThat(spec.pages().get(1).name()).isEqualTo("Subscription");

            assertThat(spec.apis()).hasSize(1);
            assertThat(spec.apis().get(0).name()).isEqualTo("获取套餐列表");
            assertThat(spec.apis().get(0).method()).isEqualTo("GET");
            assertThat(spec.apis().get(0).responseShape()).containsKey("plans");

            assertThat(spec.features()).containsExactly("套餐对比", "支付弹窗", "订单筛选");

            assertThat(spec.style()).isNotNull();
            assertThat(spec.style().theme()).isEqualTo("#6366f1");
            assertThat(spec.style().themeName()).isEqualTo("indigo");
        }

        @Test
        @DisplayName("模型返回带嵌套结构的 JSON 时，内层 Record 也正确解析")
        void shouldParseNestedRecords() {
            String json = """
                    {
                      "appName": "极简博客",
                      "description": "一个轻量博客系统",
                      "pages": [
                        {"name": "Blog", "route": "/blog", "description": "博客列表", "components": ["PostCard"]}
                      ],
                      "apis": [],
                      "features": [],
                      "style": {"theme": "#3b82f6", "themeName": "blue", "layout": "responsive", "fontFamily": "sans-serif"}
                    }
                    """;

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(json))
                            .build());

            RequirementSpec spec = analyzer.analyze("帮我做博客");

            // PageSpec 内层 Record 验证
            RequirementSpec.PageSpec page = spec.pages().get(0);
            assertThat(page).isNotNull();
            assertThat(page.name()).isEqualTo("Blog");
            assertThat(page.components()).contains("PostCard");

            // StyleSpec 内层 Record 验证
            RequirementSpec.StyleSpec style = spec.style();
            assertThat(style.theme()).isEqualTo("#3b82f6");
            assertThat(style.layout()).isEqualTo("responsive");
        }
    }

    // ==================== 执行规划 Agent ====================

    @Nested
    @DisplayName("执行规划 Agent（结构化输出）")
    class ExecutionPlannerTests {

        private ExecutionPlanner planner;

        @BeforeEach
        void setUp() {
            when(promptLoader.loadSystemPrompt("execution-planning"))
                    .thenReturn("你是一个架构师");

            planner = AiServices.builder(ExecutionPlanner.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(id -> promptLoader.loadSystemPrompt("execution-planning"))
                    .build();
        }

        @Test
        @DisplayName("模型返回文件清单 JSON 时，应正确解析为 PlanResult")
        void shouldParsePlanResult() {
            String json = """
                    {
                      "framework": "react-vite-ts",
                      "packageManager": "npm",
                      "files": [
                        {
                          "path": "package.json",
                          "purpose": "项目配置",
                          "fileType": "config",
                          "dependencies": [],
                          "required": true
                        },
                        {
                          "path": "src/styles/globals.css",
                          "purpose": "全局样式",
                          "fileType": "style",
                          "dependencies": [],
                          "required": true
                        },
                        {
                          "path": "src/components/PlanCard.tsx",
                          "purpose": "套餐卡片组件",
                          "fileType": "component",
                          "dependencies": ["src/styles/globals.css"],
                          "required": true
                        },
                        {
                          "path": "src/App.tsx",
                          "purpose": "应用入口",
                          "fileType": "entry",
                          "dependencies": ["src/components/PlanCard.tsx"],
                          "required": true
                        }
                      ],
                      "generationOrder": [
                        "package.json",
                        "src/styles/globals.css",
                        "src/components/PlanCard.tsx",
                        "src/App.tsx"
                      ],
                      "buildCommands": ["npm install", "npm run build"]
                    }
                    """;

            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from(json))
                            .build());

            PlanResult plan = planner.plan("请规划以下需求的文件清单");

            // 验证顶层字段
            assertThat(plan.framework()).isEqualTo("react-vite-ts");
            assertThat(plan.packageManager()).isEqualTo("npm");
            assertThat(plan.files()).hasSize(4);
            assertThat(plan.buildCommands()).containsExactly("npm install", "npm run build");

            // 验证文件顺序：config → style → component → entry
            assertThat(plan.files().get(0).path()).isEqualTo("package.json");
            assertThat(plan.files().get(0).fileType()).isEqualTo("config");
            assertThat(plan.files().get(1).path()).isEqualTo("src/styles/globals.css");
            assertThat(plan.files().get(1).fileType()).isEqualTo("style");
            assertThat(plan.files().get(2).path()).isEqualTo("src/components/PlanCard.tsx");
            assertThat(plan.files().get(2).fileType()).isEqualTo("component");
            assertThat(plan.files().get(3).path()).isEqualTo("src/App.tsx");
            assertThat(plan.files().get(3).fileType()).isEqualTo("entry");

            // 验证依赖关系：PlanCard 依赖 globals.css，App 依赖 PlanCard
            assertThat(plan.files().get(2).dependencies()).contains("src/styles/globals.css");
            assertThat(plan.files().get(3).dependencies()).contains("src/components/PlanCard.tsx");

            // 验证生成顺序
            assertThat(plan.generationOrder()).containsExactly(
                    "package.json", "src/styles/globals.css",
                    "src/components/PlanCard.tsx", "src/App.tsx");
        }
    }

    // ==================== 代码生成 Agent（工具调用） ====================

    @Disabled("需要精确 mock AiServices 工具调用链，实际验证请用 AgentIntegrationTest（真实 AI 调用）")
    @Nested
    @DisplayName("代码生成 Agent（带 @Tool 方法）")
    class CodeGenAgentTests {

        private CodeGenAgent agent;

        @BeforeEach
        void setUp() {
            when(promptLoader.loadSystemPrompt("code-generation"))
                    .thenReturn("你是一个前端工程师，使用 writeFile 工具写入代码");

            agent = AiServices.builder(CodeGenAgent.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(id -> promptLoader.loadSystemPrompt("code-generation"))
                    .tools(fileTools, projectContextTools)
                    .maxToolCallingRoundTrips(5)
                    .build();
        }

        @Test
        @DisplayName("模型调用 writeFile 工具时，应返回含行数的成功消息")
        void shouldCallWriteFileAndReturnSuccess() {
            when(chatModel.chat(any(ChatRequest.class)))
                    .thenReturn(ChatResponse.builder()
                            .aiMessage(AiMessage.from("已生成 PlanCard.tsx 文件"))
                            .build());

            // 模拟 writeFile 工具被调用后的返回值
            when(fileTools.writeFile("src/components/PlanCard.tsx",
                    "import React from 'react';\nexport default PlanCard;"))
                    .thenReturn("文件写入成功: src/components/PlanCard.tsx（2 行）");

            String result = agent.generate("生成 PlanCard.tsx 文件");

            assertThat(result).isNotNull();
        }
    }
}
