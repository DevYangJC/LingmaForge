package com.lingmaforge.backend.workbench.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.lingmaforge.backend.common.api.Result;
import com.lingmaforge.backend.common.model.ChatMessageResponse;
import com.lingmaforge.backend.common.model.CreateDialogRequest;
import com.lingmaforge.backend.common.model.DialogResponse;
import com.lingmaforge.backend.common.model.SendMessageRequest;
import com.lingmaforge.backend.workbench.service.ChatService;

/**
 * 对话相关 REST / SSE 接口。
 *
 * <p>接口约定：
 * <ul>
 *   <li>POST /api/chat/dialog —— 创建会话，返回 dialogId</li>
 *   <li>POST /api/chat/{dialogId}/send —— 发消息并打开 SSE 流（直接返回 SseEmitter）</li>
 *   <li>POST /api/chat/{dialogId}/stop —— 停止回复</li>
 *   <li>GET /api/chat/{dialogId}/messages —— 查询历史消息</li>
 *   <li>GET /api/chat/dialogs?projectId=xx —— 查询会话列表</li>
 * </ul>
 *
 * <p><b>端点设计说明</b>：清单原列 {@code POST /api/chat/send} + {@code GET /api/chat/stream/{dialogId}}
 * 两个端点；但闲聊是流式回复，发消息即开流，分离 send/stream 会引入"消息已发但 SSE 未连上时
 * token 丢失"的竞态。故改为 {@code POST /api/chat/{dialogId}/send} 直接返回 SseEmitter，
 * 更符合 SSE 流式回复的自然语义。</p>
 *
 * <p>SSE 端点直接返回 {@link SseEmitter}，不包裹 {@link Result}；非 SSE 端点返回 {@link Result}。</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 创建对话会话。
     *
     * @param request 创建请求
     * @return 会话视图
     */
    @PostMapping("/dialog")
    public Result<DialogResponse> createDialog(@Valid @RequestBody CreateDialogRequest request) {
        String dialogId = chatService.createDialog(request.projectId(), request.title());
        String title = (request.title() != null && !request.title().isBlank())
                ? request.title() : "新对话";
        // 简化：返回最小视图，前端拿到 dialogId 即可发消息
        return Result.ok(new DialogResponse(
                dialogId,
                request.projectId(),
                title,
                "active",
                null));
    }

    /**
     * 发送消息并打开 SSE 流式回复。
     *
     * @param dialogId 会话 ID
     * @param request  消息请求
     * @return SseEmitter
     */
    @PostMapping("/{dialogId}/send")
    public SseEmitter sendMessage(@PathVariable String dialogId,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(dialogId, request.message());
    }

    /**
     * 停止回复。
     *
     * @param dialogId 会话 ID
     * @return 空 Result
     */
    @PostMapping("/{dialogId}/stop")
    public Result<Void> stop(@PathVariable String dialogId) {
        chatService.stopDialog(dialogId);
        return Result.ok(null);
    }

    /**
     * 查询会话历史消息。
     *
     * @param dialogId 会话 ID
     * @return 消息列表
     */
    @GetMapping("/{dialogId}/messages")
    public Result<List<ChatMessageResponse>> getMessages(@PathVariable String dialogId) {
        List<ChatMessageResponse> messages = chatService.getMessages(dialogId).stream()
                .map(ChatMessageResponse::from)
                .toList();
        return Result.ok(messages);
    }

    /**
     * 查询会话列表，可按项目过滤。
     *
     * @param projectId 项目 ID，可空
     * @return 会话列表
     */
    @GetMapping("/dialogs")
    public Result<List<DialogResponse>> listDialogs(
            @RequestParam(required = false) Long projectId) {
        List<DialogResponse> dialogs = chatService.listDialogs(projectId).stream()
                .map(DialogResponse::from)
                .toList();
        return Result.ok(dialogs);
    }
}
