package com.lingmaforge.testconfig;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.lingmaforge.backend.workbench.ai.dialog.IntentResult;
import com.lingmaforge.backend.workbench.ai.factory.AgentFactory;
import com.lingmaforge.backend.workbench.ai.service.IntentAnalyzer;
import com.lingmaforge.backend.workbench.ai.service.IterationAgent;

/**
 * 测试专用 AgentFactory 覆盖配置——使意图识别固定返回 {@code MODIFY_CODE}、
 * 迭代修改 Agent 返回固定文本 {@code "修改完成"}。
 *
 * <p>与 {@link StubChatAgentConfig} 同构，用于 {@code ChatFlowIntegrationTest} 的
 * 迭代修改端到端场景：驱动 {@code DialogRouter → IntentDetectionNode → DelegateIterateNode}
 * 路径，验证 {@code DelegateIterateNode} 在 mock 模型 + mock 沙箱下的完整流转。</p>
 *
 * <p><b>为何放在 {@code com.lingmaforge.testconfig} 包</b>：与
 * {@link StubChatAgentConfig} 同理——该包不在主应用 {@code @ComponentScan}
 * （{@code basePackages = "com.lingmaforge.backend"}）扫描范围内，Spring Boot 不会
 * 自动检测本配置。只有显式声明 {@code @Import(StubIterateAgentConfig.class)} 的测试类
 * 才会加载本配置，从而精确限定 mock 仅作用于迭代修改场景，不影响 NoOp 降级等其他场景。</p>
 *
 * <p><b>覆盖范围</b>：</p>
 * <ul>
 *   <li>{@code createIntentAnalyzer()} 返回固定 {@code modify_code} 意图的 mock analyzer</li>
 *   <li>{@code createIterationAgent()} 返回固定文本的 mock {@link IterationAgent}</li>
 *   <li>{@code createChatReplyAgent()} / 其他方法保持 lenient 默认，不干扰</li>
 * </ul>
 *
 * <p>声明为 {@code @Primary} 以在导入它的上下文中覆盖真实 {@link AgentFactory} Bean。</p>
 *
 * @see StubChatAgentConfig
 */
@TestConfiguration
public class StubIterateAgentConfig {

    @Bean
    @Primary
    public AgentFactory agentFactory() {
        AgentFactory factory = mock(AgentFactory.class);

        // 意图识别：固定返回 modify_code
        IntentAnalyzer analyzer = mock(IntentAnalyzer.class);
        lenient().when(analyzer.analyze(anyString()))
                .thenReturn(new IntentResult("modify_code", 0.95));
        when(factory.createIntentAnalyzer()).thenReturn(analyzer);

        // 迭代修改 Agent：返回固定文本
        IterationAgent iterateAgent = mock(IterationAgent.class);
        when(iterateAgent.modify(anyString()))
                .thenReturn("修改完成");
        when(factory.createIterationAgent()).thenReturn(iterateAgent);

        return factory;
    }
}
