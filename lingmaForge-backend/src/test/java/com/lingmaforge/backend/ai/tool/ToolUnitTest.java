package com.lingmaforge.backend.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.lingmaforge.backend.workbench.ai.observer.GenerationContext;
import com.lingmaforge.backend.workbench.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.common.model.Patch;
import com.lingmaforge.backend.workbench.ai.tool.FileTools;
import com.lingmaforge.backend.workbench.ai.tool.IterationTools;
import com.lingmaforge.backend.workbench.ai.tool.ProjectContextTools;
import com.lingmaforge.backend.workbench.service.ProjectFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Tool 方法单元测试。
 *
 * <p>使用 Mockito 模拟 ProjectFileService 和 GenerationStreamEmitter，
 * 验证每个工具方法的输入参数处理、返回值格式和副作用（写文件、发SSE）。</p>
 */
@DisplayName("@Tool 工具体系测试")
@ExtendWith(MockitoExtension.class)
class ToolUnitTest {

    private static final Logger log = LoggerFactory.getLogger(ToolUnitTest.class);

    @Mock private ProjectFileService projectFileService;
    @Mock private GenerationStreamEmitter emitter;

    private FileTools fileTools;
    private ProjectContextTools projectContextTools;
    private IterationTools iterationTools;

    private static final Long PROJECT_ID = 1L;
    private static final String TASK_ID = "test-task";

    @BeforeEach
    void setUp() {
        fileTools = new FileTools(projectFileService);
        projectContextTools = new ProjectContextTools(null, projectFileService);
        iterationTools = new IterationTools(projectFileService);
        GenerationContext.set(PROJECT_ID, TASK_ID, emitter);

        log.info("========== 工具测试初始化 ==========");
        log.info("  PROJECT_ID: {}, TASK_ID: {}", PROJECT_ID, TASK_ID);
        log.info("  被测工具: FileTools (writeFile/patchFile/validateCode)");
        log.info("           ProjectContextTools (readFileContext)");
        log.info("           IterationTools (searchCode)");
        log.info("  模拟组件: ProjectFileService + GenerationStreamEmitter (Mockito)");
        log.info("======================================");
    }

    @AfterEach
    void tearDown() { GenerationContext.clear(); }

    @Nested
    @DisplayName("writeFile 工具")
    class WriteFileTests {

        @Test
        @DisplayName("写入文件后应返回成功消息含行数")
        void shouldReturnSuccessWithLineCount() {
            String content = "import React from 'react';\n\nconst App = () => <div>Hello</div>;\n\nexport default App;";
            when(projectFileService.writeFile(anyLong(), anyString(), anyString(), eq("new"))).thenReturn(4);

            log.info("--- writeFile 测试 ---");
            log.info("  输入: path=src/App.tsx, content={}行", content.lines().count());

            String result = fileTools.writeFile("src/App.tsx", content);

            log.info("  输出: {}", result);
            assertThat(result).contains("文件写入成功");
            assertThat(result).contains("src/App.tsx");
            assertThat(result).contains("4 行");
            verify(projectFileService).writeFile(PROJECT_ID, "src/App.tsx", content, "new");
            log.info("  [OK] 写入成功，ProjectFileService被调用1次");
        }

        @Test
        @DisplayName("写入文件后应推送 SSE file 事件")
        void shouldEmitFileEvent() {
            String content = "export default {}";
            when(projectFileService.writeFile(anyLong(), anyString(), anyString(), eq("new"))).thenReturn(1);

            log.info("--- writeFile SSE推送测试 ---");
            log.info("  输入: path=src/types.ts, content={}行", content.lines().count());

            fileTools.writeFile("src/types.ts", content);

            verify(emitter).emitFile("src/types.ts", content, "new");
            log.info("  [OK] SSE file事件已推送 (path=src/types.ts, status=new)");
        }
    }

    @Nested
    @DisplayName("patchFile 工具")
    class PatchFileTests {

        @Test
        @DisplayName("应用补丁后应返回成功消息含修改数")
        void shouldApplyPatchesAndReturnCount() {
            List<Patch> patches = List.of(
                    new Patch(3, "color: red;", "color: blue;"),
                    new Patch(8, "margin: 0;", "margin: 16px;"));
            when(projectFileService.patchFile(anyLong(), anyString(), any())).thenReturn(2);
            String newContent = ":root { --color: blue; }";
            when(projectFileService.readFile(anyLong(), anyString())).thenReturn(newContent);

            log.info("--- patchFile 测试 ---");
            log.info("  输入: path=src/styles/globals.css, 补丁{}个", patches.size());
            patches.forEach(p -> log.info("    行{}: '{}' -> '{}'", p.line(), p.old(), p.newContent()));

            String result = fileTools.patchFile("src/styles/globals.css", patches);

            log.info("  输出: {}", result);
            assertThat(result).contains("2 处修改");
            assertThat(result).contains("src/styles/globals.css");
            verify(emitter).emitFile("src/styles/globals.css", newContent, "modified");
            log.info("  [OK] patchFile应用2处修改，SSE已推送 (status=modified)");
        }

        @Test
        @DisplayName("空补丁列表应返回未修改消息")
        void shouldReturnNoChangeForEmptyPatches() {
            log.info("--- patchFile 空补丁测试 ---");
            log.info("  输入: path=src/styles/globals.css, 补丁列表=空");

            String result = fileTools.patchFile("src/styles/globals.css", List.of());

            log.info("  输出: {}", result);
            assertThat(result).contains("未提供补丁");
            log.info("  [OK] 空补丁列表正确处理");
        }
    }

    @Nested
    @DisplayName("validateCode 工具")
    class ValidateCodeTests {

        @Test
        @DisplayName("代码验证通过时返回'验证通过'")
        void shouldPassValidation() {
            String code = "import React from 'react';\n"
                    + "interface Props { name: string; price: number; }\n"
                    + "const PlanCard = ({ name, price }: Props) => {\n"
                    + "  return <div>{name}</div>;\n"
                    + "};\n"
                    + "export default PlanCard;";
            when(projectFileService.listFilePaths(anyLong())).thenReturn(List.of());

            log.info("--- validateCode 正常代码测试 ---");
            log.info("  输入: path=src/components/PlanCard.tsx, {}行", code.lines().count());

            String result = fileTools.validateCode("src/components/PlanCard.tsx", code);

            log.info("  输出: {}", result);
            assertThat(result).contains("验证通过");
            log.info("  [OK] 正常代码验证通过");
        }

        @Test
        @DisplayName("检测到不存在的 import 路径时应列出错误")
        void shouldDetectMissingImport() {
            String code = "import React from 'react';\n"
                    + "import NonExistent from './NonExistentFile';\n"
                    + "export default {};";
            when(projectFileService.listFilePaths(anyLong())).thenReturn(List.of("src/styles/globals.css"));

            log.info("--- validateCode 不存在import测试 ---");
            log.info("  输入: path=src/components/Foo.tsx, import='./NonExistentFile'(不在文件列表中)");

            String result = fileTools.validateCode("src/components/Foo.tsx", code);

            log.info("  输出: {}", result);
            assertThat(result).contains("验证失败");
            assertThat(result).contains("import 路径不存在");
            log.info("  [OK] 不存在的import路径被检测到");
        }

        @Test
        @DisplayName("检测到缺少 export 声明时应报告")
        void shouldDetectMissingExport() {
            String code = "import React from 'react';\nconst App = () => <div>Hello</div>;";
            when(projectFileService.listFilePaths(anyLong())).thenReturn(List.of());

            log.info("--- validateCode 缺少export测试 ---");
            log.info("  输入: path=src/App.tsx (有import无export)");

            String result = fileTools.validateCode("src/App.tsx", code);

            log.info("  输出: {}", result);
            assertThat(result).contains("验证失败");
            assertThat(result).contains("缺少 export 声明");
            log.info("  [OK] 缺少export声明被检测到");
        }

        @Test
        @DisplayName("检测到 any 类型时应报告")
        void shouldDetectAnyType() {
            String code = "const data: any = fetchData();\nexport default data;";
            when(projectFileService.listFilePaths(anyLong())).thenReturn(List.of());

            log.info("--- validateCode any类型测试 ---");
            log.info("  输入: path=src/data.ts (包含 'const data: any')");

            String result = fileTools.validateCode("src/data.ts", code);

            log.info("  输出: {}", result);
            assertThat(result).contains("验证失败");
            assertThat(result).contains("any 类型");
            log.info("  [OK] any类型使用被检测到");
        }

        @Test
        @DisplayName("外部包（react、@等）不应被视为不存在的 import")
        void shouldAllowExternalPackages() {
            String code = "import React, { useState } from 'react';\n"
                    + "import axios from 'axios';\n"
                    + "import { useForm } from '@tanstack/react-form';\n"
                    + "export default {};";
            when(projectFileService.listFilePaths(anyLong())).thenReturn(List.of());

            log.info("--- validateCode 外部包豁免测试 ---");
            log.info("  输入: path=src/App.tsx, imports=react/axios/@tanstack/* (均为npm包)");

            String result = fileTools.validateCode("src/App.tsx", code);

            log.info("  输出: {}", result);
            assertThat(result).contains("验证通过");
            log.info("  [OK] 外部npm包正确豁免，不误报");
        }
    }

    @Nested
    @DisplayName("readFileContext 工具")
    class ReadFileContextTests {

        @Test
        @DisplayName("返回已存在文件的格式化内容")
        void shouldReturnFormattedFileContents() {
            when(projectFileService.readFiles(eq(PROJECT_ID), any()))
                    .thenReturn(Map.of(
                            "src/styles/globals.css", ":root { --primary: blue; }",
                            "src/components/PlanCard.tsx", "export default PlanCard;"));

            log.info("--- readFileContext 测试 ---");
            log.info("  请求文件: [src/styles/globals.css, src/components/PlanCard.tsx]");

            String result = projectContextTools.readFileContext(
                    List.of("src/styles/globals.css", "src/components/PlanCard.tsx"));

            log.info("  输出 ({} 字符):\n{}", result.length(),
                    result.lines().map(l -> "    " + l).reduce("", (a, b) -> a + "\n" + b));
            assertThat(result).contains("--- 文件: src/styles/globals.css ---");
            assertThat(result).contains("--- 文件: src/components/PlanCard.tsx ---");
            assertThat(result).contains("export default PlanCard;");
            log.info("  [OK] 正确返回2个文件的格式化内容");
        }

        @Test
        @DisplayName("不存在的文件标注'尚未生成'")
        void shouldMarkMissingFiles() {
            when(projectFileService.readFiles(eq(PROJECT_ID), any()))
                    .thenReturn(Collections.singletonMap("src/App.tsx", null));

            log.info("--- readFileContext 文件不存在测试 ---");
            log.info("  请求文件: [src/App.tsx], 返回: null");

            String result = projectContextTools.readFileContext(List.of("src/App.tsx"));

            log.info("  输出: {}", result);
            assertThat(result).contains("src/App.tsx");
            assertThat(result).contains("尚未生成");
            log.info("  [OK] 不存在的文件正确标注");
        }
    }

    @Nested
    @DisplayName("searchCode 工具")
    class SearchCodeTests {

        @Test
        @DisplayName("找到关键词时返回匹配行及上下文")
        void shouldReturnMatchingLinesWithContext() {
            String fileContent = "line 1\nline 2\nconst price = 49;\nline 4\nline 5\n";
            when(projectFileService.readFile(PROJECT_ID, "src/api/mock.ts")).thenReturn(fileContent);

            log.info("--- searchCode 测试 ---");
            log.info("  关键词: 'price', 搜索范围: [src/api/mock.ts]");

            String result = iterationTools.searchCode("price", List.of("src/api/mock.ts"));

            log.info("  输出:\n{}", result.lines().map(l -> "    " + l).reduce("", (a, b) -> a + "\n" + b));
            assertThat(result).contains("文件: src/api/mock.ts");
            assertThat(result).contains("行 3");
            assertThat(result).contains(">>> const price = 49;");
            assertThat(result).contains("line 2");
            assertThat(result).contains("line 4");
            log.info("  [OK] 找到1处匹配(行3)，含+/-2行上下文");
        }

        @Test
        @DisplayName("未找到关键词时返回提示")
        void shouldReturnNotFoundMessage() {
            when(projectFileService.readFile(PROJECT_ID, "src/App.tsx")).thenReturn("export default App;");

            log.info("--- searchCode 未找到测试 ---");
            log.info("  关键词: 'nonexistent', 搜索范围: [src/App.tsx]");

            String result = iterationTools.searchCode("nonexistent", List.of("src/App.tsx"));

            log.info("  输出: {}", result);
            assertThat(result).contains("未找到包含关键词的代码");
            log.info("  [OK] 关键词未找到时正确返回提示");
        }
    }
}
