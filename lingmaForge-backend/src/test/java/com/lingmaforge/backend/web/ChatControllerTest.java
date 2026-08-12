package com.lingmaforge.backend.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.lingmaforge.backend.common.exception.BusinessException;
import com.lingmaforge.backend.common.exception.GlobalExceptionHandler;
import com.lingmaforge.backend.common.exception.ResultCode;
import com.lingmaforge.backend.workbench.entity.ChatMessageEntity;
import com.lingmaforge.backend.workbench.entity.DialogEntity;
import com.lingmaforge.backend.workbench.service.ChatService;
import com.lingmaforge.backend.workbench.web.ChatController;

/**
 * {@link ChatController} MockMvc 单元测试。
 *
 * <p>采用 {@link MockMvcBuilders#standaloneSetup} 而非 {@code @WebMvcTest}：
 * {@code LingmaForgeBackendApplication} 上的 {@code @ComponentScan} 会扫描全包组件，
 * 导致 {@code @WebMvcTest} 的 {@code TypeExcludeFilter} 失效，间接拉起
 * {@code ChatReplyNode} → {@code AgentFactory} → {@code FileTools} →
 * {@code ProjectFileServiceImpl} → {@code ProjectFileMapper} 的依赖链，
 * 而 Mapper 需要 MyBatis 的 {@code sqlSessionFactory}（切片测试中不可用）。
 * standaloneSetup 不加载 Spring 上下文，仅实例化控制器 + Mock 依赖 +
 * {@link GlobalExceptionHandler} 作为 ControllerAdvice，验证 REST 契约与参数校验。</p>
 *
 * <p>不测 SSE 实际流（MockMvc 不适合测 SSE），只验证 send 端点返回 SseEmitter。</p>
 */
@DisplayName("ChatController REST 接口测试")
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private MockMvc mockMvc;

    @Mock private ChatService chatService;

    @BeforeEach
    void setUp() {
        ChatController controller = new ChatController(chatService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("创建会话 POST /api/chat/dialog")
    class CreateDialog {

        @Test
        @DisplayName("带 projectId 与 title 创建成功 → 200 + dialogId")
        void shouldCreateDialogWithProjectAndTitle() throws Exception {
            when(chatService.createDialog(eq(1L), eq("我的会话")))
                    .thenReturn("abc123");

            mockMvc.perform(post("/api/chat/dialog")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"projectId\":1,\"title\":\"我的会话\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.dialogId").value("abc123"))
                    .andExpect(jsonPath("$.data.title").value("我的会话"))
                    .andExpect(jsonPath("$.data.status").value("active"));
        }

        @Test
        @DisplayName("title 为空 → 仍 200（title 可空，默认'新对话'）")
        void shouldCreateDialogWithoutTitle() throws Exception {
            when(chatService.createDialog(eq(null), eq(null)))
                    .thenReturn("def456");

            mockMvc.perform(post("/api/chat/dialog")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.dialogId").value("def456"))
                    .andExpect(jsonPath("$.data.title").value("新对话"));
        }
    }

    @Nested
    @DisplayName("发送消息 POST /api/chat/{dialogId}/send")
    class SendMessage {

        @Test
        @DisplayName("message 为空 → 200 + 校验失败 code（全局异常处理器返回 OK 状态）")
        void shouldReturnValidationFailWhenMessageBlank() throws Exception {
            mockMvc.perform(post("/api/chat/abc/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.PARAM_INVALID.getCode()));
        }

        @Test
        @DisplayName("合法消息 → 返回 SseEmitter（200）")
        void shouldReturnSseEmitterForValidMessage() throws Exception {
            // 立即 complete，避免 MockMvc 异步等待挂起
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            when(chatService.sendMessage(eq("abc"), eq("你好")))
                    .thenReturn(emitter);

            mockMvc.perform(post("/api/chat/abc/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"你好\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("停止回复 POST /api/chat/{dialogId}/stop")
    class Stop {

        @Test
        @DisplayName("停止成功 → 200")
        void shouldStopSuccessfully() throws Exception {
            doNothing().when(chatService).stopDialog(anyString());

            mockMvc.perform(post("/api/chat/abc/stop"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("查询历史消息 GET /api/chat/{dialogId}/messages")
    class GetMessages {

        @Test
        @DisplayName("会话不存在 → 业务异常")
        void shouldThrowWhenDialogNotFound() throws Exception {
            when(chatService.getMessages("missing"))
                    .thenThrow(new BusinessException(ResultCode.DIALOG_NOT_FOUND));

            mockMvc.perform(get("/api/chat/missing/messages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.DIALOG_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("返回消息列表（按时间升序）")
        void shouldReturnMessages() throws Exception {
            ChatMessageEntity userMsg = new ChatMessageEntity();
            userMsg.setId(1L);
            userMsg.setRole("user");
            userMsg.setContent("你好");
            userMsg.setCreatedAt(LocalDateTime.of(2026, 8, 12, 10, 0));

            ChatMessageEntity assistantMsg = new ChatMessageEntity();
            assistantMsg.setId(2L);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent("你好！我是灵码工坊助手。");
            assistantMsg.setCreatedAt(LocalDateTime.of(2026, 8, 12, 10, 0, 5));

            when(chatService.getMessages("abc"))
                    .thenReturn(List.of(userMsg, assistantMsg));

            mockMvc.perform(get("/api/chat/abc/messages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].role").value("user"))
                    .andExpect(jsonPath("$.data[1].role").value("assistant"));
        }
    }

    @Nested
    @DisplayName("查询会话列表 GET /api/chat/dialogs")
    class ListDialogs {

        @Test
        @DisplayName("按 projectId 过滤 → 返回会话列表")
        void shouldReturnDialogsByProject() throws Exception {
            DialogEntity dialog = new DialogEntity();
            dialog.setDialogId("abc");
            dialog.setProjectId(1L);
            dialog.setTitle("项目会话");
            dialog.setStatus("active");
            dialog.setCreatedAt(LocalDateTime.of(2026, 8, 12, 9, 0));

            when(chatService.listDialogs(1L))
                    .thenReturn(List.of(dialog));

            mockMvc.perform(get("/api/chat/dialogs").param("projectId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].dialogId").value("abc"))
                    .andExpect(jsonPath("$.data[0].title").value("项目会话"));
        }

        @Test
        @DisplayName("不带 projectId → 返回全部会话")
        void shouldReturnAllDialogsWithoutProjectId() throws Exception {
            when(chatService.listDialogs(any()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/chat/dialogs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }
}
