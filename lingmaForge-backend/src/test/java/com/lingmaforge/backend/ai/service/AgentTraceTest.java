package com.lingmaforge.backend.ai.service;

import java.util.UUID;

import com.lingmaforge.backend.ai.factory.AgentFactory;
import com.lingmaforge.backend.ai.observer.GenerationContext;
import com.lingmaforge.backend.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.model.CreateProjectRequest;
import com.lingmaforge.backend.service.ProjectFileService;
import com.lingmaforge.backend.service.ProjectService;
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

/**
 * Agent 调用链追踪测试 —— 打印每一步的输入和输出数据。
 *
 * <p>目的：亲眼看到大模型返回的到底是什么、writeFile收到的content是什么、代码是否干净。</p>
 *
 * <p>运行方式：
 * <pre>
 *   LINGMA_INTEGRATION_TEST=true DEEPSEEK_API_KEY=你的密钥
 *   ./mvnw test -Dtest=AgentTraceTest -DfailIfNoTests=false
 * </pre>
 *
 * <p>关键日志项：
 * <ol>
 *   <li>[LangChain4j] Request —— 发给大模型的请求体</li>
 *   <li>[LangChain4j] Response —— 大模型的响应，看finish_reason</li>
 *   <li>writeFile被调用时收到的content参数</li>
 *   <li>content是否以```开头（应为false=代码干净）</li>
 *   <li>content是否包含客套话（应为false=代码干净）</li>
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
        projectService.createProject(new CreateProjectRequest("追踪测试-" + UUID.randomUUID(), "追踪", "react-vite-ts"));
        var projects = projectService.list();
        projectId = projects.get(projects.size() - 1).getId();
        GenerationContext.set(projectId, "trace-" + UUID.randomUUID(), new NoOpEmitter());
    }

    @AfterEach
    void tearDown() { GenerationContext.clear(); }

    @Test
    @DisplayName("追踪 agent.generate() -> writeFile 完整参数流")
    void traceSingleCodeGeneration() {
        String prompt = "Generate file: src/App.tsx\n"
                + "File type: entry\n"
                + "File description: React app entry, define routing and layout\n"
                + "Tech stack: React 18 + TypeScript + CSS Variables\n"
                + "Call writeFile tool directly to write the complete code.";

        log.info("================================================================");
        log.info("  第1步: 你的代码调用 agent.generate(prompt)");
        log.info("----------------------------------------------------------------");
        log.info("  prompt 内容（你给Agent的输入）: {}", prompt);
        log.info("================================================================");

        log.info("================================================================");
        log.info("  第2步: AiServices 代理类接管");
        log.info("----------------------------------------------------------------");
        log.info("  AiServices 自动做了:");
        log.info("  1) 扫描 @Tool 方法 -> 生成 JSON Schema");
        log.info("  2) 拼装 System Prompt + User Prompt + tools");
        log.info("  3) 发送 HTTP POST 给大模型");
        log.info("  4) 请向下查看日志中的 [LangChain4j] Request/Response");
        log.info("================================================================");

        CodeGenAgent agent = agentFactory.createCodeGenAgent();
        String agentResult = agent.generate(prompt);

        log.info("================================================================");
        log.info("  第3步: agent.generate() 返回了");
        log.info("----------------------------------------------------------------");
        log.info("  Agent 文本返回值: \"{}\"", agentResult);
        log.info("  注意: 这个 String 不是代码! 代码已在 writeFile 的 content 参数里");
        log.info("  被 LangChain4j 直接传给了 FileTools.writeFile() 并写入了磁盘。");
        log.info("================================================================");

        String diskContent = projectFileService.readFile(projectId, "src/App.tsx");

        if (diskContent != null) {
            boolean startsWithBlock = diskContent.trim().startsWith("```");
            boolean hasPleasantries = diskContent.contains("好的") || diskContent.contains("以下是") || diskContent.contains("让我为你");
            long lineCount = diskContent.lines().count();

            log.info("================================================================");
            log.info("  第4步: 验证磁盘上的文件内容");
            log.info("----------------------------------------------------------------");
            log.info("  文件路径: src/App.tsx / 总行数: {} / 以```开头?: {} / 含客套话?: {}",
                    lineCount, startsWithBlock, hasPleasantries);
            log.info("  (以```开头应为false=代码干净! 含客套话应为false=代码干净!)");
            log.info("----------------------------------------------------------------");
            log.info("  文件前5行:");
            diskContent.lines().limit(5).forEach(l -> log.info("    {}", l));
            log.info("================================================================");
        } else {
            log.warn("磁盘上未找到 src/App.tsx —— 大模型可能没有调用 writeFile 工具!");
        }
    }

    private static class NoOpEmitter implements GenerationStreamEmitter {
        @Override public void emitNode(String n, String t, String tt) {}
        @Override public void emitFile(String p, String c, String s) {}
        @Override public void emitLog(String t) {}
        @Override public void complete(String u, Integer po, Integer bt) {}
        @Override public void error(String m) {}
    }
}
