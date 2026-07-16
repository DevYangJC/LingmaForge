package com.lingmaforge.backend.workbench.web;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lingmaforge.backend.common.api.Result;
import com.lingmaforge.backend.common.exception.BusinessException;
import com.lingmaforge.backend.common.exception.ResultCode;
import com.lingmaforge.backend.common.model.CreateGenerationRequest;
import com.lingmaforge.backend.common.model.GenerationTaskResponse;
import com.lingmaforge.backend.common.model.GenerationTaskStatusResponse;
import com.lingmaforge.backend.common.model.IterateRequest;
import com.lingmaforge.backend.workbench.service.GenerationService;
import com.lingmaforge.backend.workbench.service.GenerationTaskService;

import reactor.core.publisher.Flux;

/**
 * 代码生成相关的 REST / SSE 接口。
 *
 * <p>接口约定：
 * <ul>
* <li>POST /api/generation/create —— 创建生成任务，返回 taskId</li>
 *   <li>GET /api/stream/generation/{taskId} —— SSE 流式推送生成进度</li>
 *   <li>POST /api/generation/iterate —— 创建迭代修改任务</li>
 *   <li>GET /api/stream/iteration/{taskId} —— SSE 流式推送迭代进度</li>
 *   <li>DELETE /api/generation/{taskId}/stop —— 停止生成</li>
 *   <li>GET /api/generation/{taskId}/status —— 查询任务状态</li>
 * </ul>
 * SSE 端点返回 {@code Flux<ServerSentEvent<String>>}（Spring WebFlux 响应式返回值，
 * 在 Servlet 模式下经 ReactiveTypeHandler 适配为异步流式 SSE），事件类型统一为
 * {@code token / thinking / tool_call / done / error}，不包裹 {@link Result}。</p>
 */
@RestController
@RequestMapping("/api")
public class GenerationController {

    private final GenerationService generationService;
    private final GenerationTaskService generationTaskService;

    public GenerationController(GenerationService generationService,
            GenerationTaskService generationTaskService) {
        this.generationService = generationService;
        this.generationTaskService = generationTaskService;
    }

    /**
     * 创建生成任务。
     *
     * @param request 创建请求
     * @return 任务 ID
     */
    @PostMapping("/generation/create")
    public Result<GenerationTaskResponse> create(@Valid @RequestBody CreateGenerationRequest request) {
        String taskId = generationService.createGeneration(request.projectId(), request.prompt());
        return Result.ok(new GenerationTaskResponse(taskId));
    }

    /**
     * 订阅生成进度的 SSE 流。
     *
     * <p>返回 {@code Flux<ServerSentEvent<String>>}，{@code produces=text/event-stream}。
     * 统一封装事件类型：token / thinking / tool_call / done / error。</p>
     *
     * @param taskId 任务 ID
     * @return 响应式 SSE 事件流
     */
    @GetMapping(value = "/stream/generation/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamGeneration(@PathVariable String taskId) {
        return generationService.streamGeneration(taskId);
    }

    /**
     * 创建迭代修改任务。
     *
     * @param request 迭代请求
     * @return 任务 ID
     */
    @PostMapping("/generation/iterate")
    public Result<GenerationTaskResponse> iterate(@Valid @RequestBody IterateRequest request) {
        String taskId = generationService.iterate(request.projectId(), request.prompt());
        return Result.ok(new GenerationTaskResponse(taskId));
    }

    /**
     * 订阅迭代修改进度的 SSE 流。
     *
     * @param taskId 任务 ID
     * @return 响应式 SSE 事件流
     */
    @GetMapping(value = "/stream/iteration/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamIteration(@PathVariable String taskId) {
        return generationService.streamIteration(taskId);
    }

    /**
     * 停止生成。
     *
     * @param taskId 任务 ID
     * @return 操作结果
     */
    @DeleteMapping("/generation/{taskId}/stop")
    public Result<Void> stop(@PathVariable String taskId) {
        generationService.stopGeneration(taskId);
        return Result.ok(null);
    }

    /**
     * 查询生成任务状态。
     *
     * @param taskId 任务 ID
     * @return 任务状态信息
     */
    @GetMapping("/generation/{taskId}/status")
    public Result<GenerationTaskStatusResponse> getStatus(@PathVariable String taskId) {
        var task = generationTaskService.getByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        return Result.ok(GenerationTaskStatusResponse.from(task));
    }
}
