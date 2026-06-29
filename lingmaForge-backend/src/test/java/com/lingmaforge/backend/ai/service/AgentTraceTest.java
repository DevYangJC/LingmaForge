package com.lingmaforge.backend.ai.service;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.lingmaforge.backend.generation.agent.factory.AgentFactory;
import com.lingmaforge.backend.generation.stream.GenerationContext;
import com.lingmaforge.backend.generation.stream.GenerationStreamEmitter;
import com.lingmaforge.backend.project.dto.CreateProjectRequest;
import com.lingmaforge.backend.project.service.ProjectFileService;
import com.lingmaforge.backend.project.service.ProjectService;

/**
 * 🔍 Agent 调用链追踪测试 —— 打印每一步的输入和输出数据。
 *
 * <p><b>目的</b>：让你亲眼看到大模型返回的到底是什么、
 * writeFile 收到的 content 是什么、代码到底干不干净。</p>
 *
 * <p><b>运行方式</b>（IDEA 或命令行）：
 * <pre>
 *   LINGMA_INTEGRATION_TEST=true
 *   DEEPSEEK_API_KEY=你的密钥
 *   ./mvnw test -Dtest=AgentTraceTest -DfailIfNoTests=false
 * </pre>
 *
 * <p><b>重点看日志里的这几行</b>：
 * <ol>
 *   <li>{@code [LangChain4j] Request:} —— 发给 DeepSeek 的请求体，看 tools 字段怎么定义的</li>
 *   <li>{@code [LangChain4j] Response:} —— DeepSeek 返回的响应，看 finish_reason 是 stop 还是 tool_calls</li>
 *   <li>{@code ╔══ writeFile 被大模型调用} —— {@code writeFile} 工具收到的 content 参数长什么样</li>
 *   <li>content 是否以 ``` 开头？→ {@code false}（代码没有被 markdown 包裹）</li>
 *   <li>content 是否包含 "好的"？→ {@code false}（代码没有被中文废话污染）</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "LINGMA_INTEGRATION_TEST", matches = "true")
@DisplayName("Agent 调用链追踪")
class AgentTraceTest {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceTest.class);

    @Autowired private AgentFactory agentFactory;
    @Autowired private ProjectService projectService;
    @Autowired private ProjectFileService projectFileService;

    private Long projectId;

    @BeforeEach
    void setUp() {
        projectService.createProject(
                new CreateProjectRequest("追踪测试-" + UUID.randomUUID(), "追踪", "react-vite-ts"));
        var projects = projectService.list();
        projectId = projects.get(projects.size() - 1).getId();
        GenerationContext.set(projectId, "trace-" + UUID.randomUUID(), new NoOpEmitter());
    }

    @AfterEach
    void tearDown() {
        GenerationContext.clear();
    }

    /**
     * 🔍 追踪一次代码生成 Agent 调用的完整链路。
     *
     * <p>大模型被要求生成 {@code src/App.tsx}，观察它是否调用 {@code writeFile}、
     * 调用的 content 是否干净。</p>
     */
    @Test
    @DisplayName("追踪 agent.generate() → writeFile 的完整参数流")
    void traceSingleCodeGeneration() {

        // ====== 入口日志：打印发送给 Agent 的 prompt ======
        String prompt = """
                请生成文件: src/App.tsx
                文件类型: entry
                文件描述: React 应用入口，定义基本的路由结构和 Layout

                技术栈: React 18 + TypeScript + CSS 变量
                请直接调用 writeFile 工具写入完整代码。
                """;

        log.info("""
                ╔══════════════════════════════════════════════════════════╗
                ║  第 1 步：你的代码调用 agent.generate(prompt)             ║
                ╠══════════════════════════════════════════════════════════╣
                ║  prompt 内容（你给 Agent 的输入）→
                ║  {}
                ╚══════════════════════════════════════════════════════════╝
                """, prompt.replace("\n", "\n║  "));

        // ====== 调用 Agent ======
        log.info("""
                ╔══════════════════════════════════════════════════════════╗
                ║  第 2 步：AiServices 代理类接管                            ║
                ╠══════════════════════════════════════════════════════════╣
                ║  AiServices 自动做了：                                   ║
                ║  ① 扫描 @Tool 方法 → 生成 JSON Schema                    ║
                ║  ② 拼装 System Prompt + User Prompt + tools             ║
                ║  ③ 发送 HTTP POST 给 DeepSeek                           ║
                ║  ④ 请向下查看日志中的 [LangChain4j] Request/Response    ║
                ╚══════════════════════════════════════════════════════════╝
                """);

        CodeGenAgent agent = agentFactory.createCodeGenAgent();
        String agentResult = agent.generate(prompt);

        // ====== 出口日志：打印 Agent 的 String 返回值 ======
        log.info("""
                ╔══════════════════════════════════════════════════════════╗
                ║  第 3 步：agent.generate() 返回了                          ║
                ╠══════════════════════════════════════════════════════════╣
                ║  Agent 文本返回值 → "{}"
                ╠══════════════════════════════════════════════════════════╣
                ║  ⚠️ 注意：这个 String 不是代码！                           ║
                ║  代码已经在 writeFile 的 content 参数里                    ║
                ║  被 LangChain4j 直接传给了 FileTools.writeFile()           ║
                ║  然后写入了磁盘。                                          ║
                ║  这个 String 只是 AI 最后说的一句话（如"已生成"）         ║
                ╚══════════════════════════════════════════════════════════╝
                """, agentResult);

        // ====== 验证磁盘上的文件 ======
        String diskContent = projectFileService.readFile(projectId, "src/App.tsx");

        if (diskContent != null) {
            boolean startsWithBlock = diskContent.trim().startsWith("```");
            boolean hasChinesePrefix = diskContent.contains("好的")
                    || diskContent.contains("以下是")
                    || diskContent.contains("让我为你");
            long lineCount = diskContent.lines().count();

            log.info("""
                    ╔══════════════════════════════════════════════════════════╗
                    ║  第 4 步：验证磁盘上的文件内容                             ║
                    ╠══════════════════════════════════════════════════════════╣
                    ║  文件路径 → src/App.tsx
                    ║  总行数   → {}
                    ║  以 ``` 开头?   → {}  ← 如果是 false，说明代码干净！
                    ║  含中文客套话?  → {}  ← 如果是 false，说明代码干净！
                    ╠══════════════════════════════════════════════════════════╣
                    ║  文件前 5 行内容 →                                       ║
                    {}
                    ╚══════════════════════════════════════════════════════════╝
                    """,
                    lineCount,
                    startsWithBlock,
                    hasChinesePrefix,
                    diskContent.lines().limit(5).map(l -> "║  " + l)
                            .reduce("", (a, b) -> a + "\n" + b));
        } else {
            log.warn("⚠️ 磁盘上未找到 src/App.tsx —— 大模型可能没有调用 writeFile 工具！");
        }
    }

    /** 空实现：只打日志，不推 SSE。 */
    private static class NoOpEmitter implements GenerationStreamEmitter {
        @Override public void emitNode(String n, String t, String tt) {}
        @Override public void emitFile(String p, String c, String s) {}
        @Override public void emitLog(String t) {}
        @Override public void complete(String u, Integer po, Integer bt) {}
        @Override public void error(String m) {}
    }
}
