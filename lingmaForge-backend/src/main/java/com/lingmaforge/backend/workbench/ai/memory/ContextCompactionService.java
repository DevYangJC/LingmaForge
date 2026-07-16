package com.lingmaforge.backend.workbench.ai.memory;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.lingmaforge.backend.infra.config.ContextCompactionProperties;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Layer 2 & 3：上下文自动 / 手动压缩服务——移植自 zero-code {@code ContextCompactionService}。
 *
 * <p>当对话上下文的 token 估算值超过阈值时，调用轻量 LLM 生成对话摘要，
 * 用一条摘要消息替换全部历史消息，实现"无限会话"。</p>
 *
 * <p>Layer 2 自动压缩：{@link #autoCompactIfNeeded} —— 每次调 agent 前检查并触发。
 * Layer 3 手动压缩：{@link #forceCompact} —— 通过 API 显式触发（后续对接 controller）。</p>
 */
@Service
public class ContextCompactionService {

    private static final Logger log = LoggerFactory.getLogger(ContextCompactionService.class);

    private static final String SUMMARY_PROMPT = """
            请总结以下对话的关键信息，确保包含：
            1) 已完成的操作（创建/修改/删除了哪些文件），每次修改必须记录【修改前的原始值 → 修改后的新值】的完整对照，
               例如：「将首页标题从『静夜思』修改为『测试』」，而不是只写「将标题修改为『测试』」。
               这一点非常重要，因为用户可能需要回退到之前的状态，必须保留原始值。
            2) 当前项目状态（项目结构、关键组件）
            3) 用户的核心需求和偏好
            4) 尚未完成的任务或待解决的问题
            5) 关键数据的变更历史链（按时间顺序），包括文本内容、配置参数、样式属性等的前后值

            要求：
            - 对于每一处内容修改，必须同时保留修改前和修改后的值，确保可回溯可回退
            - 使用「A → B」格式记录变更对照
            - 简洁但不遗漏任何修改细节，使用中文回复

            对话内容：
            """;

    private static final String COMPACT_MARKER = "[对话已压缩，完整记录保留在事件日志中]";

    private final ContextCompactionProperties compactionProperties;
    private final ChatModel summaryChatModel;
    private final CompactingChatMemoryStore compactingStore;

    public ContextCompactionService(
            ContextCompactionProperties compactionProperties,
            ChatModel summaryChatModel,
            CompactingChatMemoryStore compactingStore) {
        this.compactionProperties = compactionProperties;
        this.summaryChatModel = summaryChatModel;
        this.compactingStore = compactingStore;
    }

    /**
     * Layer 2：自动压缩。检查当前上下文 token 估算是否超过阈值，超过则执行压缩。
     *
     * @param chatMemory 当前会话的 ChatMemory
     * @param memoryId   会话 ID
     * @return true 如果执行了压缩
     */
    public boolean autoCompactIfNeeded(MessageWindowChatMemory chatMemory, String memoryId) {
        List<ChatMessage> messages = chatMemory.messages();
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        int estimatedTokens = estimateTokens(messages);
        int threshold = compactionProperties.tokenThreshold() > 0
                ? compactionProperties.tokenThreshold()
                : ContextCompactionProperties.DEFAULT_TOKEN_THRESHOLD;

        if (estimatedTokens <= threshold) {
            return false;
        }

        // Layer 1：压缩前先做微压缩，减少送入摘要模型的工具结果噪声
        compactingStore.compactAndPersist(memoryId);

        log.info("自动压缩触发，memoryId={}, estimatedTokens={}, threshold={}",
                memoryId, estimatedTokens, threshold);
        return doCompact(chatMemory, memoryId, chatMemory.messages());
    }

    /**
     * Layer 3：手动压缩。立即执行压缩。
     *
     * @param memoryId 会话 ID
     * @return true 如果执行了压缩
     */
    public boolean forceCompact(String memoryId) {
        List<ChatMessage> messages = compactingStore.getMessages(memoryId);
        if (messages == null || messages.size() <= 2) {
            log.info("消息太少，无需压缩，memoryId={}", memoryId);
            return false;
        }
        compactingStore.compactAndPersist(memoryId);
        messages = compactingStore.getMessages(memoryId);

        String summary = generateSummary(messages);
        List<ChatMessage> compacted = List.of(
                UserMessage.from(COMPACT_MARKER + "\n\n" + summary));
        compactingStore.updateMessages(memoryId, compacted);

        log.info("手动压缩完成，memoryId={}, 原消息数={}, 压缩后=1", memoryId, messages.size());
        return true;
    }

    private boolean doCompact(MessageWindowChatMemory chatMemory, String memoryId,
            List<ChatMessage> messages) {
        try {
            String summary = generateSummary(messages);

            chatMemory.clear();
            chatMemory.add(UserMessage.from(COMPACT_MARKER + "\n\n" + summary));

            log.info("压缩完成，memoryId={}, 原消息数={}, 摘要长度={}",
                    memoryId, messages.size(), summary.length());
            return true;
        } catch (Exception e) {
            log.error("压缩失败，memoryId={}, 保持原始上下文不变", memoryId, e);
            return false;
        }
    }

    private String generateSummary(List<ChatMessage> messages) {
        StringBuilder conversationText = new StringBuilder();
        for (ChatMessage msg : messages) {
            conversationText.append(formatMessage(msg)).append("\n");
        }
        String text = conversationText.toString();
        int maxChars = compactionProperties.maxSummaryInputChars() > 0
                ? compactionProperties.maxSummaryInputChars()
                : ContextCompactionProperties.DEFAULT_MAX_SUMMARY_INPUT_CHARS;
        if (text.length() > maxChars) {
            text = text.substring(text.length() - maxChars);
        }

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from(SUMMARY_PROMPT + text)))
                .build();
        ChatResponse response = summaryChatModel.chat(request);
        String summary = response.aiMessage().text();

        if (summary == null || summary.isBlank()) {
            summary = "无法生成摘要，请查看事件日志获取完整记录。";
        }
        return summary;
    }

    private String formatMessage(ChatMessage msg) {
        if (msg instanceof UserMessage userMsg) {
            return "[用户] " + userMsg.singleText();
        } else if (msg instanceof AiMessage aiMsg) {
            if (aiMsg.hasToolExecutionRequests()) {
                return "[AI 工具调用] " + aiMsg.toolExecutionRequests().toString();
            }
            return "[AI] " + (aiMsg.text() != null ? aiMsg.text() : "");
        } else if (msg instanceof dev.langchain4j.data.message.ToolExecutionResultMessage toolMsg) {
            String text = toolMsg.text();
            if (text != null && text.length() > 2000) {
                text = text.substring(0, 1500) + "...[截断]";
            }
            return "[工具结果: " + toolMsg.toolName() + "] " + text;
        }
        return msg.toString();
    }

    /**
     * 粗略估算 token 数量：中英混合场景约 3 字符/token。
     */
    private int estimateTokens(List<ChatMessage> messages) {
        int totalChars = 0;
        for (ChatMessage msg : messages) {
            totalChars += msg.toString().length();
        }
        return totalChars / 3;
    }
}