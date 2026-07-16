package com.lingmaforge.backend.workbench.ai.stream;

import java.util.function.Consumer;

import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamRegistry;

/**
 * 流式桥接上下文——聚合一次 {@code TokenStream} 桥接所需的所有依赖与回调钩子。
 *
 * <p>设计思想（移植自 zero-code 的 {@code WorkflowContext.streamConsumer} 模式）：
 * 节点只需填好上下文，交给 {@link StreamingBridge}，不再手写
 * {@code onPartialResponse / onPartialThinking / onCompleteResponse / onError}
 * 四件套回调与停止信号检测。</p>
 *
 * <p>三个钩子对应 TokenStream 的三个生命周期：
 * <ul>
 *   <li>{@link #onToken} —— 每收到一个 token（由节点决定如何发射：通用文本/文件代码）</li>
 *   <li>{@link #onComplete} —— 正常完成（由节点决定后续：completableFuture.complete / 发总结文字）</li>
 *   <li>{@link #onStop} —— 收到停止信号中途打断（传入已收集的部分文本）</li>
 * </ul>
 */
public class StreamingContext {

    private final GenerationStreamEmitter emitter;
    private final String nodeName;
    private final String taskId;
    private final GenerationStreamRegistry stopRegistry;
    private final Consumer<String> onToken;
    private final Runnable onComplete;
    private final Consumer<String> onStop;

    private StreamingContext(Builder builder) {
        this.emitter = builder.emitter;
        this.nodeName = builder.nodeName;
        this.taskId = builder.taskId;
        this.stopRegistry = builder.stopRegistry;
        this.onToken = builder.onToken;
        this.onComplete = builder.onComplete;
        this.onStop = builder.onStop;
    }

    public GenerationStreamEmitter emitter() { return emitter; }
    public String nodeName() { return nodeName; }
    public String taskId() { return taskId; }
    public GenerationStreamRegistry stopRegistry() { return stopRegistry; }
    public Consumer<String> onToken() { return onToken; }
    public Runnable onComplete() { return onComplete; }
    public Consumer<String> onStop() { return onStop; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private GenerationStreamEmitter emitter;
        private String nodeName;
        private String taskId;
        private GenerationStreamRegistry stopRegistry;
        private Consumer<String> onToken = token -> {};
        private Runnable onComplete = () -> {};
        private Consumer<String> onStop = text -> {};

        public Builder emitter(GenerationStreamEmitter emitter) { this.emitter = emitter; return this; }
        public Builder nodeName(String nodeName) { this.nodeName = nodeName; return this; }
        public Builder taskId(String taskId) { this.taskId = taskId; return this; }
        public Builder stopRegistry(GenerationStreamRegistry stopRegistry) { this.stopRegistry = stopRegistry; return this; }
        public Builder onToken(Consumer<String> onToken) { this.onToken = onToken; return this; }
        public Builder onComplete(Runnable onComplete) { this.onComplete = onComplete; return this; }
        public Builder onStop(Consumer<String> onStop) { this.onStop = onStop; return this; }

        public StreamingContext build() {
            if (emitter == null) throw new IllegalStateException("emitter is required");
            if (nodeName == null) throw new IllegalStateException("nodeName is required");
            if (taskId == null) throw new IllegalStateException("taskId is required");
            if (stopRegistry == null) throw new IllegalStateException("stopRegistry is required");
            return new StreamingContext(this);
        }
    }
}