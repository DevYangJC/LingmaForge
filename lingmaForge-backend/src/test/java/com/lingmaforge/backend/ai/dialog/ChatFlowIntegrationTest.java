package com.lingmaforge.backend.ai.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.lingmaforge.backend.workbench.ai.dialog.DialogIntent;
import com.lingmaforge.backend.workbench.ai.dialog.DialogRouter;
import com.lingmaforge.backend.workbench.ai.dialog.DialogState;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;
import com.lingmaforge.testconfig.StubChatAgentConfig;

/**
 * 闲聊对话端到端集成测试。
 *
 * <p>验证 {@code DialogRouter → IntentDetectionNode → ChatReplyNode → SSE} 端到端路径，
 * 覆盖两种场景：</p>
 * <ol>
 *   <li><b>Mock 模型场景</b>：通过 {@link StubChatAgentConfig} 用 {@code @Primary} Bean
 *       覆盖真实 {@link AgentFactory}，{@code createIntentAnalyzer()} 返回固定 CHAT 意图的
 *       mock analyzer，{@code createChatReplyAgent()} 返回携带 {@link StubTokenStream} 的
 *       mock agent。驱动图执行 → 验证 emitter 收到 token + complete 事件、
 *       {@code DELEGATE_RESULT} 写入完整回复。</li>
 *   <li><b>NoOp 降级场景</b>：使用真实 Spring 上下文（测试环境无 API Key →
 *       {@code NoOpStreamingModel}）。意图识别在 {@code IntentDetectionNode} 被捕获兜底为
 *       CHAT，闲聊回复节点调用流式模型时 {@code TokenStream.start()} 同步抛
 *       {@code IllegalStateException}（NoOpStreamingModel 在 {@code chat()} 内直接 throw，
 *       不会触发 {@code onError} 回调）→ 被 {@code ChatReplyNode} 的统一 try-catch 捕获 →
 *       {@code emitter.error()} → {@code DELEGATE_RESULT} 为 {@code "[闲聊回复失败]"}。
 *       验证图不崩溃、错误正确传播。</li>
 * </ol>
 *
 * <p><b>为什么直接驱动图而非用 MockMvc 测 SSE</b>：MockMvc 的异步分发与 SSE emitter 的
 * fork-join 线程模型不兼容，容易挂起。这里直接驱动
 * {@code dialogRouter.getCompiledGraph().stream(inputs)}（同步迭代，节点内部
 * {@code future.join()} 阻塞至流结束），用 {@link CapturingEmitter} 捕获事件，
 * 断言事件序列与最终状态。</p>
 *
 * <p><b>同步性注记</b>：虽然 {@code DialogRouter} 用 {@code node_async} 把节点派发到
 * fork-join 线程，但 {@code CompiledGraph.stream()} 返回的 {@code Iterator} 会阻塞到
 * 每个节点执行完成才产出下一个 {@code NodeOutput}，故测试线程在
 * {@code reduce(...).orElseThrow()} 处自然等待图执行结束，无需额外同步原语。</p>
 */
@DisplayName("闲聊对话端到端集成测试")
@ActiveProfiles("test")
class ChatFlowIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ChatFlowIntegrationTest.class);

    /**
     * Mock 模型场景：用 {@link StubChatAgentConfig} 覆盖 {@link AgentFactory}，
     * 让 ChatReplyNode 走 {@link StubTokenStream}。
     */
    @Nested
    @DisplayName("场景一：Mock 模型端到端")
    @SpringBootTest
    @Import(StubChatAgentConfig.class)
    class MockModelScenario {

        @Autowired private ApplicationContext context;

        @Test
        @DisplayName("闲聊意图 → 图执行 → emitter 收到 token + complete 事件")
        void shouldStreamChatReplyEndToEnd() {
            DialogRouter router = context.getBean(DialogRouter.class);
            GenerationStreamRegistry registry = context.getBean(GenerationStreamRegistry.class);
            CapturingEmitter emitter = new CapturingEmitter();
            String dialogId = "test-dialog-mock";
            registry.register(dialogId, emitter);

            log.info("--- 场景一：Mock 模型端到端 ---");
            Map<String, Object> inputs = Map.of(
                    DialogState.DIALOG_ID, dialogId,
                    DialogState.USER_MESSAGE, "你好");

            var finalState = router.getCompiledGraph().stream(inputs)
                    .stream()
                    .reduce((first, second) -> second)
                    .orElseThrow()
                    .state();

            // 验证意图与回复
            assertThat(finalState.intent()).hasValue(DialogIntent.CHAT);
            assertThat(finalState.delegateResult())
                    .as("DELEGATE_RESULT 应为 StubTokenStream 携带的完整回复")
                    .hasValueSatisfying(s ->
                            assertThat(s).contains("灵码工坊助手"));

            // 验证 emitter 事件序列
            assertThat(emitter.chatTokens)
                    .as("应收到至少一个 chat token")
                    .isNotEmpty();
            assertThat(emitter.completeResponse)
                    .as("应收到 complete 事件携带完整回复")
                    .isEqualTo("你好！我是灵码工坊助手。");
            assertThat(emitter.errors)
                    .as("不应有错误事件")
                    .isEmpty();

            log.info("[OK] 场景一：intent=CHAT, tokens={}, complete='{}'",
                    emitter.chatTokens.size(), emitter.completeResponse);
            registry.unregister(dialogId);
        }
    }

    /**
     * NoOp 降级场景：使用真实 Spring 上下文（无 API Key → NoOpStreamingModel）。
     */
    @Nested
    @DisplayName("场景二：NoOp 降级端到端")
    @SpringBootTest
    class NoOpFallbackScenario {

        @Autowired private ApplicationContext context;

        @Test
        @DisplayName("无 API Key → NoOpStreamingModel 抛异常 → 错误优雅传播")
        void shouldPropagateErrorGracefullyOnNoOpModel() {
            DialogRouter router = context.getBean(DialogRouter.class);
            GenerationStreamRegistry registry = context.getBean(GenerationStreamRegistry.class);
            CapturingEmitter emitter = new CapturingEmitter();
            String dialogId = "test-dialog-noop";
            registry.register(dialogId, emitter);

            log.info("--- 场景二：NoOp 降级端到端 ---");
            Map<String, Object> inputs = Map.of(
                    DialogState.DIALOG_ID, dialogId,
                    DialogState.USER_MESSAGE, "你好");

            var finalState = router.getCompiledGraph().stream(inputs)
                    .stream()
                    .reduce((first, second) -> second)
                    .orElseThrow()
                    .state();

            // IntentDetectionNode 捕获 NoOpModel 异常 → 兜底返回 CHAT
            assertThat(finalState.intent()).hasValue(DialogIntent.CHAT);

            // ChatReplyNode 调用 NoOpStreamingModel → chat() 在 start() 内同步抛
            // IllegalStateException（不走 onError 回调）→ 被 execute() 的统一 try-catch 捕获 →
            // emitter.error() → DELEGATE_RESULT = "[闲聊回复失败]"
            assertThat(finalState.delegateResult())
                    .as("NoOp 降级时 DELEGATE_RESULT 应为失败占位文本")
                    .hasValueSatisfying(s ->
                            assertThat(s).contains("失败"));

            // emitter 应收到 error 事件
            assertThat(emitter.errors)
                    .as("应收到至少一个错误事件")
                    .isNotEmpty();

            log.info("[OK] 场景二：NoOp 降级，delegateResult='{}', errors={}",
                    finalState.delegateResult().orElse(""), emitter.errors.size());
            registry.unregister(dialogId);
        }
    }

    // ==================== 捕获 SSE 事件的 emitter ====================

    /**
     * 捕获 {@link GenerationStreamEmitter} 调用，用于断言事件序列。
     *
     * <p>实现所有接口方法，只记录闲聊相关事件；其余方法空实现。</p>
     */
    static class CapturingEmitter implements GenerationStreamEmitter {

        final List<String> chatTokens = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        volatile String completeResponse;

        @Override
        public void emitChatToken(String token) {
            chatTokens.add(token);
        }

        @Override
        public void emitChatComplete(String fullResponse) {
            this.completeResponse = fullResponse;
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }

        // 以下方法本测试不关注，空实现
        @Override public void emitNode(String n, String t, String tt) {}
        @Override public void emitFile(String p, String c, String s) {}
        @Override public void emitLog(String t) {}
        @Override public void complete(String u, Integer po, Integer bt) {}
        @Override public void emitModification(String n, String t, String tt,
                List<com.lingmaforge.backend.common.model.FileModification> mods) {}
        @Override public void emitNodeStart(String nodeName, String title) {}
        @Override public void emitNodeEnd(String nodeName) {}
        @Override public void emitThinking(String nodeName, String token) {}
        @Override public void emitFileToken(String path, String token) {}
        @Override public void emitFileComplete(String path) {}
    }
}
