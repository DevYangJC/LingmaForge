package com.lingmaforge.backend.ai.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lingmaforge.backend.workbench.ai.dialog.ChatReplyNode;
import com.lingmaforge.backend.workbench.ai.dialog.DelegateCodegenNode;
import com.lingmaforge.backend.workbench.ai.dialog.DelegateIterateNode;
import com.lingmaforge.backend.workbench.ai.dialog.DialogIntent;
import com.lingmaforge.backend.workbench.ai.dialog.DialogRouter;
import com.lingmaforge.backend.workbench.ai.dialog.DialogState;
import com.lingmaforge.backend.workbench.ai.dialog.IntentDetectionNode;
import com.lingmaforge.backend.workbench.ai.dialog.IntentResult;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.service.IntentAnalyzer;

/**
 * DialogRouter 对话图测试。
 *
 * <p>验证对话图的节点注册、条件路由逻辑与意图识别兜底，确保三条意图路径正确分流。
 * 仿照 {@code CodeGenPipelineTest} 的模式：mock 节点依赖，直接调用路由方法断言。</p>
 */
@DisplayName("DialogRouter 对话图测试")
@ExtendWith(MockitoExtension.class)
class DialogRouterTest {

    private static final Logger log = LoggerFactory.getLogger(DialogRouterTest.class);

    @Mock private AgentFactory agentFactory;

    private DialogRouter router;

    @BeforeEach
    void setUp() throws Exception {
        // setUp 中的 IntentDetectionNode 只用于图编译与路由测试（不触发 execute），
        // 但其构造器会调用 createIntentAnalyzer()，故需打桩返回一个 mock，
        // 避免 analyzer 字段为 null（防止后续误触发图执行时 NPE）。
        IntentAnalyzer stubAnalyzer = mock(IntentAnalyzer.class);
        when(agentFactory.createIntentAnalyzer()).thenReturn(stubAnalyzer);

        // 真实节点（桩节点无需 mock，IntentDetectionNode 需 mock AgentFactory）
        IntentDetectionNode intentDetectionNode = new IntentDetectionNode(agentFactory);
        DelegateCodegenNode delegateCodegenNode = new DelegateCodegenNode();
        DelegateIterateNode delegateIterateNode = new DelegateIterateNode();
        ChatReplyNode chatReplyNode = new ChatReplyNode();

        router = new DialogRouter(
                intentDetectionNode, delegateCodegenNode,
                delegateIterateNode, chatReplyNode);
        router.init();

        log.info("========== DialogRouter 初始化 ==========");
        log.info("  节点链: intent_detection -> [条件边] -> (delegate_codegen | delegate_iterate | chat_reply) -> END");
        log.info("==========================================");
    }

    @Nested
    @DisplayName("图编译")
    class GraphCompilation {
        @Test
        @DisplayName("对话图应编译成功")
        void shouldCompileSuccessfully() {
            assertThat(router.getCompiledGraph()).isNotNull();
            log.info("[OK] 对话图编译成功");
        }

        @Test
        @DisplayName("Mermaid 图应包含四个节点")
        void mermaidGraphShouldContainAllNodes() {
            String mermaid = router.getCompiledGraph().getGraph(
                    org.bsc.langgraph4j.GraphRepresentation.Type.MERMAID).content();
            assertThat(mermaid).contains(IntentDetectionNode.NODE_NAME);
            assertThat(mermaid).contains(DelegateCodegenNode.NODE_NAME);
            assertThat(mermaid).contains(DelegateIterateNode.NODE_NAME);
            assertThat(mermaid).contains(ChatReplyNode.NODE_NAME);
            log.info("[OK] Mermaid 图包含全部 4 个节点");
            log.info("  {}", mermaid);
        }
    }

    @Nested
    @DisplayName("条件路由：按意图分流")
    class ConditionalRouting {

        /**
         * 构造一个携带指定意图的 DialogState。
         */
        private DialogState stateWithIntent(DialogIntent intent) {
            Map<String, Object> data = new HashMap<>();
            data.put(DialogState.USER_MESSAGE, "test message");
            if (intent != null) {
                data.put(DialogState.INTENT, intent);
            }
            return new DialogState(data);
        }

        @Test
        @DisplayName("GENERATE_PROJECT 意图 -> 路由到 delegate_codegen")
        void shouldRouteToCodegenOnGenerateProject() {
            DialogState state = stateWithIntent(DialogIntent.GENERATE_PROJECT);
            log.info("--- 模拟生成项目意图 ---");
            String route = router.routeByIntent(state);
            assertThat(route).isEqualTo(DelegateCodegenNode.NODE_NAME);
            log.info("[OK] 路由正确：GENERATE_PROJECT -> delegate_codegen ({})", route);
        }

        @Test
        @DisplayName("MODIFY_CODE 意图 -> 路由到 delegate_iterate")
        void shouldRouteToIterateOnModifyCode() {
            DialogState state = stateWithIntent(DialogIntent.MODIFY_CODE);
            log.info("--- 模拟修改代码意图 ---");
            String route = router.routeByIntent(state);
            assertThat(route).isEqualTo(DelegateIterateNode.NODE_NAME);
            log.info("[OK] 路由正确：MODIFY_CODE -> delegate_iterate ({})", route);
        }

        @Test
        @DisplayName("CHAT 意图 -> 路由到 chat_reply")
        void shouldRouteToChatReplyOnChat() {
            DialogState state = stateWithIntent(DialogIntent.CHAT);
            log.info("--- 模拟闲聊意图 ---");
            String route = router.routeByIntent(state);
            assertThat(route).isEqualTo(ChatReplyNode.NODE_NAME);
            log.info("[OK] 路由正确：CHAT -> chat_reply ({})", route);
        }

        @Test
        @DisplayName("意图缺失 -> 回退到 chat_reply（最安全）")
        void shouldFallbackToChatWhenIntentMissing() {
            DialogState state = stateWithIntent(null);
            log.info("--- 模拟意图缺失场景 ---");
            String route = router.routeByIntent(state);
            assertThat(route).isEqualTo(ChatReplyNode.NODE_NAME);
            log.info("[OK] 意图缺失 -> 回退到 chat_reply ({})", route);
        }
    }

    @Nested
    @DisplayName("意图识别兜底")
    class IntentDetectionFallback {

        /**
         * 构造一个携带用户消息的 DialogState。
         */
        private DialogState stateWithMessage(String message) {
            Map<String, Object> data = new HashMap<>();
            data.put(DialogState.USER_MESSAGE, message);
            return new DialogState(data);
        }

        @Test
        @DisplayName("模型抛异常 -> 回退为 CHAT，置信度 0.0")
        void shouldFallbackToChatOnModelException() {
            IntentAnalyzer throwingAnalyzer = mock(IntentAnalyzer.class);
            when(throwingAnalyzer.analyze(anyString()))
                    .thenThrow(new IllegalStateException("模型不可用"));
            when(agentFactory.createIntentAnalyzer()).thenReturn(throwingAnalyzer);

            IntentDetectionNode node = new IntentDetectionNode(agentFactory);
            DialogState state = stateWithMessage("帮我生成一个商城");

            log.info("--- 模拟模型不可用场景 ---");
            Map<String, Object> result = node.execute(state);
            assertThat(result.get(DialogState.INTENT)).isEqualTo(DialogIntent.CHAT);
            assertThat(result.get(DialogState.INTENT_CONFIDENCE)).isEqualTo(0.0);
            log.info("[OK] 模型异常 -> 回退 CHAT, confidence=0.0");
        }

        @Test
        @DisplayName("模型返回未识别意图 -> 回退为 CHAT，置信度 0.0")
        void shouldFallbackToChatOnUnknownIntent() {
            IntentAnalyzer analyzer = mock(IntentAnalyzer.class);
            when(analyzer.analyze(anyString()))
                    .thenReturn(new IntentResult("unknown_intent", 0.3));
            when(agentFactory.createIntentAnalyzer()).thenReturn(analyzer);

            IntentDetectionNode node = new IntentDetectionNode(agentFactory);
            DialogState state = stateWithMessage("随便说点什么");

            log.info("--- 模拟未识别意图场景 ---");
            Map<String, Object> result = node.execute(state);
            assertThat(result.get(DialogState.INTENT)).isEqualTo(DialogIntent.CHAT);
            assertThat(result.get(DialogState.INTENT_CONFIDENCE)).isEqualTo(0.0);
            log.info("[OK] 未识别意图 -> 回退 CHAT, confidence=0.0");
        }

        @Test
        @DisplayName("模型返回合法意图 -> 正确写入状态")
        void shouldWriteCorrectIntentOnSuccess() {
            IntentAnalyzer analyzer = mock(IntentAnalyzer.class);
            when(analyzer.analyze(anyString()))
                    .thenReturn(new IntentResult("generate_project", 0.95));
            when(agentFactory.createIntentAnalyzer()).thenReturn(analyzer);

            IntentDetectionNode node = new IntentDetectionNode(agentFactory);
            DialogState state = stateWithMessage("帮我生成一个订阅商店");

            log.info("--- 模拟正常识别场景 ---");
            Map<String, Object> result = node.execute(state);
            assertThat(result.get(DialogState.INTENT)).isEqualTo(DialogIntent.GENERATE_PROJECT);
            assertThat(result.get(DialogState.INTENT_CONFIDENCE)).isEqualTo(0.95);
            log.info("[OK] 正常识别 -> GENERATE_PROJECT, confidence=0.95");
        }

        @Test
        @DisplayName("模型返回意图但缺 confidence 字段 -> 意图保留，置信度回退 0.0")
        void shouldFallbackConfidenceWhenNull() {
            IntentAnalyzer analyzer = mock(IntentAnalyzer.class);
            when(analyzer.analyze(anyString()))
                    .thenReturn(new IntentResult("chat", null));
            when(agentFactory.createIntentAnalyzer()).thenReturn(analyzer);

            IntentDetectionNode node = new IntentDetectionNode(agentFactory);
            DialogState state = stateWithMessage("你好");

            log.info("--- 模拟缺 confidence 字段场景 ---");
            Map<String, Object> result = node.execute(state);
            assertThat(result.get(DialogState.INTENT)).isEqualTo(DialogIntent.CHAT);
            assertThat(result.get(DialogState.INTENT_CONFIDENCE)).isEqualTo(0.0);
            log.info("[OK] confidence 缺失 -> 意图保留 CHAT, confidence 回退 0.0");
        }
    }

    @Nested
    @DisplayName("端到端图执行")
    class EndToEndExecution {

        /**
         * 构造一个独立于 setUp 的 DialogRouter，其 IntentDetectionNode 注入了指定 analyzer。
         * 用于验证图能真正跑通 intent_detection → 条件边 → delegate → END。
         */
        private DialogRouter routerWithAnalyzer(IntentAnalyzer analyzer) throws Exception {
            when(agentFactory.createIntentAnalyzer()).thenReturn(analyzer);
            IntentDetectionNode intentDetectionNode = new IntentDetectionNode(agentFactory);
            DialogRouter r = new DialogRouter(
                    intentDetectionNode, new DelegateCodegenNode(),
                    new DelegateIterateNode(), new ChatReplyNode());
            r.init();
            return r;
        }

        @Test
        @DisplayName("闲聊意图端到端 -> 跑通 intent_detection → chat_reply → END")
        void shouldExecuteEndToEndForChat() throws Exception {
            IntentAnalyzer analyzer = mock(IntentAnalyzer.class);
            when(analyzer.analyze(anyString()))
                    .thenReturn(new IntentResult("chat", 0.9));
            DialogRouter r = routerWithAnalyzer(analyzer);

            log.info("--- 端到端执行（闲聊意图）---");
            var results = r.getCompiledGraph().stream(
                    Map.of(DialogState.USER_MESSAGE, "你好"));
            var finalState = results.stream()
                    .reduce((first, second) -> second)
                    .orElseThrow()
                    .state();

            assertThat(finalState.intent()).hasValue(DialogIntent.CHAT);
            assertThat(finalState.delegateResult()).hasValueSatisfying(s ->
                    assertThat(s).contains("[桩]").contains("Phase 2"));
            log.info("[OK] 端到端：intent=CHAT, delegateResult 已写入桩结果");
        }

        @Test
        @DisplayName("生成意图端到端 -> 跑通 intent_detection → delegate_codegen → END")
        void shouldExecuteEndToEndForGenerateProject() throws Exception {
            IntentAnalyzer analyzer = mock(IntentAnalyzer.class);
            when(analyzer.analyze(anyString()))
                    .thenReturn(new IntentResult("generate_project", 0.95));
            DialogRouter r = routerWithAnalyzer(analyzer);

            log.info("--- 端到端执行（生成意图）---");
            var results = r.getCompiledGraph().stream(
                    Map.of(DialogState.USER_MESSAGE, "帮我生成一个商城"));
            var finalState = results.stream()
                    .reduce((first, second) -> second)
                    .orElseThrow()
                    .state();

            assertThat(finalState.intent()).hasValue(DialogIntent.GENERATE_PROJECT);
            assertThat(finalState.delegateResult()).hasValueSatisfying(s ->
                    assertThat(s).contains("[桩]").contains("Phase 4"));
            log.info("[OK] 端到端：intent=GENERATE_PROJECT, delegateResult 已写入桩结果");
        }
    }
}
