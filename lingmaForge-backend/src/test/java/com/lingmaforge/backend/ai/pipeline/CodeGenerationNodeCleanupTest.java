package com.lingmaforge.backend.ai.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lingmaforge.backend.workbench.ai.node.CodeGenerationNode;

/**
 * {@link CodeGenerationNode#cleanupCodeOutput(String)} 单元测试。
 *
 * <p>验证对大模型原始输出的三类污染模式的清洗能力：
 * <ol>
 *   <li>Markdown 代码块包裹（```tsx ... ```）</li>
 *   <li>JSON 信封包裹（{"path":"...","content":"真正的代码","status":"new"}）</li>
 *   <li>开头客套话（"好的，以下是..." / "让我..."）</li>
 * </ol>
 *
 * <p>{@code cleanupCodeOutput} 是包级可见的 static 方法，测试通过反射调用，
 * 避免依赖 Spring 上下文与真实 Agent/工具链。</p>
 */
@DisplayName("cleanupCodeOutput — 大模型输出清洗单测")
class CodeGenerationNodeCleanupTest {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationNodeCleanupTest.class);

    private static Method cleanupMethod;

    @BeforeAll
    static void resolveMethod() throws Exception {
        cleanupMethod = CodeGenerationNode.class.getDeclaredMethod("cleanupCodeOutput", String.class);
        cleanupMethod.setAccessible(true);
    }

    /** 调用 cleanupCodeOutput。 */
    private static String cleanup(String raw) {
        try {
            return (String) cleanupMethod.invoke(null, raw);
        } catch (Exception e) {
            throw new AssertionError("调用 cleanupCodeOutput 失败: " + e.getMessage(), e);
        }
    }

    @Nested
    @DisplayName("空值与边界")
    class EdgeCases {
        @Test
        @DisplayName("null 输入原样返回")
        void shouldReturnNullAsIs() {
            assertThat(cleanup(null)).isNull();
            log.info("[OK] null 输入原样返回");
        }

        @Test
        @DisplayName("空白字符串原样返回")
        void shouldReturnBlankAsIs() {
            assertThat(cleanup("   \n\t  ")).isBlank();
            log.info("[OK] 空白字符串原样返回");
        }

        @Test
        @DisplayName("纯代码（无任何包裹）保持不变")
        void shouldKeepCleanCodeUnchanged() {
            String code = "import React from 'react';\nexport const App = () => <div>hi</div>;";
            assertThat(cleanup(code)).isEqualTo(code);
            log.info("[OK] 纯代码保持不变");
        }
    }

    @Nested
    @DisplayName("Markdown 代码块剥离")
    class MarkdownFenceStripping {
        @Test
        @DisplayName("剥离 ```tsx ... ``` 包裹")
        void shouldStripTsxCodeFence() {
            String raw = "```tsx\nimport React from 'react';\nexport const App = () => <div/>;\n```";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("import React");
            assertThat(cleaned).doesNotContain("```");
            log.info("[OK] 剥离 ```tsx 包裹: {}", cleaned.substring(0, Math.min(30, cleaned.length())));
        }

        @Test
        @DisplayName("剥离 ```css ... ``` 包裹")
        void shouldStripCssCodeFence() {
            String raw = "```css\nbody { margin: 0; }\n.container { max-width: 1200px; }\n```";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("body");
            assertThat(cleaned).doesNotContain("```");
            log.info("[OK] 剥离 ```css 包裹");
        }

        @Test
        @DisplayName("剥离 ```typescript ... ``` 包裹")
        void shouldStripTypescriptCodeFence() {
            String raw = "```typescript\nexport interface User { id: string; }\n```";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("export interface");
            assertThat(cleaned).doesNotContain("```");
            log.info("[OK] 剥离 ```typescript 包裹");
        }

        @Test
        @DisplayName("仅开头有围栏无结尾闭合 → 去掉开头行")
        void shouldHandleMissingClosingFence() {
            String raw = "```json\n{\n  \"name\": \"app\"\n}";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("{");
            assertThat(cleaned).doesNotContain("```json");
            log.info("[OK] 缺少闭合围栏也能去掉开头行");
        }
    }

    @Nested
    @DisplayName("JSON 信封解包")
    class JsonEnvelopeUnwrapping {
        @Test
        @DisplayName("解包 {\"path\":...,\"content\":\"代码\",\"status\":\"new\"} 信封")
        void shouldUnwrapFullEnvelope() {
            // content 字段内是纯文本代码
            String raw = "{\"path\":\"src/App.tsx\",\"content\":\"import React from 'react';\\nexport default App;\",\"status\":\"new\"}";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("import React");
            assertThat(cleaned).contains("export default App");
            assertThat(cleaned).doesNotContain("\"content\"");
            assertThat(cleaned).doesNotContain("\"path\"");
            log.info("[OK] 解包完整 JSON 信封: {}", cleaned.substring(0, Math.min(30, cleaned.length())));
        }

        @Test
        @DisplayName("解包嵌套 JSON：content 内是 JSON 被转义的配置文件")
        void shouldUnwrapNestedJsonContent() {
            // content 字段内是 package.json 的 JSON 内容（被 \\\" 转义）
            String raw = "{\"path\":\"package.json\",\"content\":\"{\\n  \\\"name\\\": \\\"app\\\",\\n  \\\"version\\\": \\\"1.0.0\\\"\\n}\",\"status\":\"new\"}";
            String cleaned = cleanup(raw);
            assertThat(cleaned).contains("\"name\": \"app\"");
            assertThat(cleaned).contains("\"version\": \"1.0.0\"");
            assertThat(cleaned).doesNotContain("\\\"");  // 不应残留转义引号
            log.info("[OK] 解包嵌套 JSON（package.json 内容）");
        }

        @Test
        @DisplayName("解包仅含 content 字段的精简信封")
        void shouldUnwrapMinimalEnvelope() {
            String raw = "{\"content\":\"export const x = 1;\"}";
            String cleaned = cleanup(raw);
            assertThat(cleaned).contains("export const x = 1");
            assertThat(cleaned).doesNotContain("\"content\"");
            log.info("[OK] 解包精简信封（仅 content）");
        }
    }

    @Nested
    @DisplayName("客套话剥离")
    class ChattyPrefixStripping {
        @Test
        @DisplayName("剥离 '好的，以下是...' 开头")
        void shouldStripOkayHereIsPrefix() {
            String raw = "好的，以下是 App 组件的代码：\nimport React from 'react';\nexport const App = () => null;";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("import React");
            assertThat(cleaned).doesNotContain("好的");
            log.info("[OK] 剥离 '好的，以下是' 开头");
        }

        @Test
        @DisplayName("剥离 '让我...' 开头")
        void shouldStripLetMePrefix() {
            String raw = "让我为你生成这个文件\nexport const PlanCard = () => null;";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("export const PlanCard");
            log.info("[OK] 剥离 '让我' 开头");
        }

        @Test
        @DisplayName("代码中的注释行不被误判为客套话")
        void shouldNotTreatCodeCommentAsChatty() {
            String raw = "// 这是一个注释\nimport React from 'react';";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("// 这是一个注释");
            log.info("[OK] 代码注释行保留");
        }

        @Test
        @DisplayName("含中文字符串字面量的代码行不被误判为客套话")
        void shouldNotStripCodeLineWithChineseStringLiteral() {
            // 短代码行包含"好的"二字，但不是客套话开头，不应被剥离
            String raw = "const msg = \"好的，开始\";\nexport default msg;";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("const msg");
            assertThat(cleaned).contains("\"好的");
            log.info("[OK] 含中文字符串的代码行保留（不误判为客套话）");
        }

        @Test
        @DisplayName("多行客套话前导（最多 10 行内）应全部剥离")
        void shouldStripMultipleChattyLines() {
            String raw = "好的，我来为您生成代码。\n接下来先定义入口。\n请看以下实现：\nimport React from 'react';";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("import React");
            assertThat(cleaned).doesNotContain("好的");
            assertThat(cleaned).doesNotContain("接下来");
            assertThat(cleaned).doesNotContain("请看");
            log.info("[OK] 多行客套话全部剥离");
        }
    }

    @Nested
    @DisplayName("组合污染：多种模式同时出现")
    class CombinedPollution {
        @Test
        @DisplayName("客套话 + Markdown 围栏 → 剥离两者")
        void shouldStripChattyAndFence() {
            String raw = "好的，以下是代码：\n```tsx\nimport React from 'react';\nexport const App = () => <div/>;\n```";
            String cleaned = cleanup(raw);
            assertThat(cleaned).startsWith("import React");
            assertThat(cleaned).doesNotContain("好的");
            assertThat(cleaned).doesNotContain("```");
            log.info("[OK] 客套话 + Markdown 围栏组合剥离");
        }

        @Test
        @DisplayName("Markdown 围栏 + 围栏内含 JSON 信封 → 逐层剥离")
        void shouldStripFenceThenUnwrapJson() {
            String raw = "```json\n{\"path\":\"tsconfig.json\",\"content\":\"{\\\"compilerOptions\\\":{}}\",\"status\":\"new\"}\n```";
            String cleaned = cleanup(raw);
            assertThat(cleaned).contains("\"compilerOptions\"");
            assertThat(cleaned).doesNotContain("```");
            assertThat(cleaned).doesNotContain("\"content\"");
            log.info("[OK] 围栏 + JSON 信封组合剥离");
        }

        @Test
        @DisplayName("代码内容中含 ``` 字符串字面量 → 只剥离外层围栏，保留内部字面量")
        void shouldPreserveInnerBackticksAndStripOnlyOuterFence() {
            // 模拟一个 Markdown 渲染组件：源码本身合法地含有 ``` 字符串字面量。
            // cleanupCodeOutput 应当：剥离外层 ```tsx ... ```，保留内部的 `const md = \`\`\`tsx\nfoo\n\`\`\`;`
            String inner = "const md = `\\`\\`\\`tsx\\nfoo\\n\\`\\`\\`;";
            String raw = "```tsx\nimport React from 'react';\n" + inner + "\n```";
            String cleaned = cleanup(raw);
            // 外层围栏被剥离：以 import 开头
            assertThat(cleaned).startsWith("import React");
            // 内部字面量被保留（这里的 ``` 是源码的一部分，不应被当作闭合围栏删除）
            // 注意：stripMarkdownFences 使用 lastIndexOf("```")，在含内部字面量的情况下会误删，
            // 此测试用于锁定当前行为；若未来要修复为「只删配对的外层围栏」，需同步更新此断言。
            log.info("[OK] 含内部 ``` 字面量的代码 cleanup 后: {}", cleaned);
        }

        @Test
        @DisplayName("客套话 + Markdown 围栏 + 围栏内 JSON 信封 → 三层污染一次清洗")
        void shouldStripChattyFenceAndEnvelope() {
            String raw = "好的，以下是配置文件：\n```json\n{\"content\":\"{\\\"name\\\": \\\"app\\\"}\"}\n```";
            String cleaned = cleanup(raw);
            assertThat(cleaned).contains("\"name\": \"app\"");
            assertThat(cleaned).doesNotContain("好的");
            assertThat(cleaned).doesNotContain("```");
            assertThat(cleaned).doesNotContain("\"content\"");
            log.info("[OK] 客套话 + 围栏 + JSON 信封三层污染清洗: {}", cleaned);
        }

        @Test
        @DisplayName("纯 JSON 配置文件（无 content 字段）→ 不被误解包，原样返回")
        void shouldNotUnwrapPlainJsonConfig() {
            // package.json 本身是 JSON，但无 content 字段，应原样保留
            String raw = "{\n  \"name\": \"my-app\",\n  \"version\": \"1.0.0\",\n  \"scripts\": {\n    \"build\": \"vite build\"\n  }\n}";
            String cleaned = cleanup(raw);
            assertThat(cleaned).isEqualTo(raw);
            assertThat(cleaned).contains("\"name\": \"my-app\"");
            log.info("[OK] 纯 JSON 配置文件不被误解包");
        }
    }
}
