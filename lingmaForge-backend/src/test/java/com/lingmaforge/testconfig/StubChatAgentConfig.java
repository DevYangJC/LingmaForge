package com.lingmaforge.testconfig;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.lingmaforge.backend.testutil.StubTokenStream;
import com.lingmaforge.backend.workbench.ai.dialog.IntentResult;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.service.ChatReplyAgent;
import com.lingmaforge.backend.workbench.ai.service.IntentAnalyzer;

/**
 * 测试专用 AgentFactory 覆盖配置——使意图识别固定返回 CHAT、闲聊回复返回
 * {@link StubTokenStream}。
 *
 * <p><b>为何放在 {@code com.lingmaforge.testconfig} 包</b>：
 * Spring Boot 测试支持会<b>自动检测</b>主应用 {@code @ComponentScan} 扫描范围
 * （本项目 {@code basePackages = "com.lingmaforge.backend"}）内的所有
 * {@code @TestConfiguration} 类，并应用到<b>所有</b> {@code @SpringBootTest} 上下文，
 * 即使未显式 {@code @Import} 也会加载。之前把本配置放在
 * {@code com.lingmaforge.backend.ai.dialog}（扫描范围内），导致 NoOp 降级场景
 * 也被注入了 mock AgentFactory，测试失效。</p>
 *
 * <p>把本配置移到 {@code com.lingmaforge.testconfig}——<b>不在</b>主应用
 * {@code @ComponentScan} 扫描范围内——Spring Boot 不再自动检测它。此时只有显式声明
 * {@code @Import(StubChatAgentConfig.class)} 的测试类才会加载本配置，从而精确限定
 * mock 仅作用于 {@code MockModelScenario}，NoOp 降级场景不受影响。</p>
 *
 * <p>声明为 {@code @Primary} 以在导入它的上下文中覆盖真实 {@link AgentFactory} Bean。
 * {@code @TestConfiguration} + {@code @Import} 会在 {@code @SpringBootTest} 上下文基础上
 * 追加该 Bean，而非替换整个配置类。</p>
 */
@TestConfiguration
public class StubChatAgentConfig {

    @Bean
    @Primary
    public AgentFactory agentFactory() {
        AgentFactory factory = mock(AgentFactory.class);

        // 意图识别：固定返回 CHAT
        IntentAnalyzer analyzer = mock(IntentAnalyzer.class);
        lenient().when(analyzer.analyze(anyString()))
                .thenReturn(new IntentResult("chat", 0.95));
        when(factory.createIntentAnalyzer()).thenReturn(analyzer);

        // 闲聊回复：返回 StubTokenStream
        ChatReplyAgent chatAgent = mock(ChatReplyAgent.class);
        when(chatAgent.reply(anyString()))
                .thenReturn(new StubTokenStream("你好！我是灵码工坊助手。"));
        when(factory.createChatReplyAgent()).thenReturn(chatAgent);

        return factory;
    }
}
