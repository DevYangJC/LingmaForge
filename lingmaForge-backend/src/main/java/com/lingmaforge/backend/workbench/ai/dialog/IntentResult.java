package com.lingmaforge.backend.workbench.ai.dialog;

import java.io.Serializable;

/**
 * 意图识别结构化输出。
 *
 * <p>由 {@code IntentAnalyzer}（LangChain4j AiServices）返回，
 * {@code intent} 为字符串以便模型输出，由 {@link DialogIntent#fromType(String)} 转枚举。
 * {@code confidence} 用 boxed {@code Double} 而非 primitive，便于检测模型缺失该字段时回退 CHAT。</p>
 *
 * @param intent     意图类型标识：generate_project / modify_code / chat
 * @param confidence 置信度 [0.0, 1.0]，模型未返回时为 null
 */
public record IntentResult(String intent, Double confidence) implements Serializable {
    private static final long serialVersionUID = 1L;
}
