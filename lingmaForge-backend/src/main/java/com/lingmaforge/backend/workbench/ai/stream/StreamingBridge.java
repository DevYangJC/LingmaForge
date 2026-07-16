package com.lingmaforge.backend.workbench.ai.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;

import dev.langchain4j.service.TokenStream;

/**
 * 流式输出基础底座——把 LangChain4j {@link TokenStream} 自动桥接到
 * 统一 SSE 事件协议（token / thinking / tool_call / done / error）。
 *
 * <p>核心职责：
 * <ul>
 *   <li>自动为 {@code onPartialResponse} 注入停止信号检测并调用 {@link StreamingContext#onToken}</li>
 *   <li>自动为 {@code onPartialThinking} 注入停止信号检测并调用 {@code emitter.emitThinking}</li>
 *   <li>在 {@code onCompleteResponse} 时调用 {@link StreamingContext#onComplete}</li>
 *   <li>在 {@code onError} 时调用 {@code emitter.error}</li>
 *   <li>中停信号触发后不再发射后续事件，并调 {@link StreamingContext#onStop} 传入已收集的部分文本</li>
 * </ul>
 *
 * <p>用法（以 CodePatchNode 为例）：
 * <pre>{@code
 * StreamingContext ctx = StreamingContext.builder()
 *     .emitter(emitter).nodeName(NODE_NAME).taskId(taskId)
 *     .stopRegistry(getStreamRegistry())
 *     .onToken(t -> emitter.emitNode(NODE_NAME, t, "TEXT"))
 *     .onComplete(() -> emitter.emitNode(NODE_NAME, "完成", "TEXT"))
 *     .build();
 * streamingBridge.bridge(editor.edit(prompt), ctx);
 * }</pre>
 *
 * <p>节点完全不需要再触碰 {@code TokenStream} 的链式回调细节。</p>
 */
@Component
public class StreamingBridge {

    private static final Logger log = LoggerFactory.getLogger(StreamingBridge.class);

    private final GenerationStreamRegistry stopRegistry;

    public StreamingBridge(GenerationStreamRegistry stopRegistry) {
        this.stopRegistry = stopRegistry;
    }

    /**
     * 把 TokenStream 桥接到统一的五类 SSE 事件流。
     *
     * <p>本方法会调用 {@link TokenStream#start()} 启动底层 HTTP SSE 连接。
     * 调用者（节点）在调用本方法后无需再触碰 TokenStream。</p>
     *
     * @param tokenStream LangChain4j TokenStream（尚未 start）
     * @param ctx         桥接上下文（含 emitter、钩子）
     */
    public void bridge(TokenStream tokenStream, StreamingContext ctx) {
        StringBuilder accumulator = new StringBuilder();
        boolean[] stopped = {false};
        String taskId = ctx.taskId();

        tokenStream
                .onPartialResponse(token -> {
                    if (stopRegistry.isStopRequested(taskId)) {
                        if (!stopped[0]) {
                            stopped[0] = true;
                            ctx.onStop().accept(accumulator.toString());
                        }
                        return;
                    }
                    accumulator.append(token);
                    ctx.onToken().accept(token);
                })
                .onPartialThinking(thinking -> {
                    if (stopped[0] || stopRegistry.isStopRequested(taskId)) return;
                    ctx.emitter().emitThinking(ctx.nodeName(), thinking.text());
                })
                .onCompleteResponse(response -> {
                    if (!stopped[0] && !stopRegistry.isStopRequested(taskId)) {
                        ctx.onComplete().run();
                    }
                })
                .onError(error -> {
                    log.error("[{}] {} 流式异常", taskId, ctx.nodeName(), error);
                    ctx.emitter().error(ctx.nodeName() + " 异常: " + error.getMessage());
                })
                .start();
    }
}