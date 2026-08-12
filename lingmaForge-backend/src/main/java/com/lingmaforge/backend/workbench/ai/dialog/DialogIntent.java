package com.lingmaforge.backend.workbench.ai.dialog;

import lombok.Getter;

/**
 * 对话意图枚举。
 *
 * <p>由 {@link IntentDetectionNode} 识别后写入 {@link DialogState#INTENT}，
 * {@code DialogRouter} 据此通过条件边路由到对应的 delegate 节点。</p>
 */
@Getter
public enum DialogIntent {

    /** 生成项目：用户要新建/生成/搭建一个项目或应用。 */
    GENERATE_PROJECT("generate_project"),
    /** 修改代码：用户要修改/调整/优化已有代码。 */
    MODIFY_CODE("modify_code"),
    /** 闲聊：问候、技术问答、概念解释等不涉及具体代码操作。 */
    CHAT("chat");

    /** 意图类型标识符（与模型输出对齐）。 */
    private final String type;

    DialogIntent(String type) {
        this.type = type;
    }

    /**
     * 根据类型标识获取枚举。
     *
     * @param type 类型标识（大小写不敏感）
     * @return 匹配的枚举；未匹配返回 {@code null}
     */
    public static DialogIntent fromType(String type) {
        if (type == null) {
            return null;
        }
        for (DialogIntent intent : values()) {
            if (intent.getType().equalsIgnoreCase(type.trim())) {
                return intent;
            }
        }
        return null;
    }
}
