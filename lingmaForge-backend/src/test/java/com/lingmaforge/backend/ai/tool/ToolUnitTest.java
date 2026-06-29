package com.lingmaforge.backend.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lingmaforge.backend.ai.observer.GenerationContext;
import com.lingmaforge.backend.ai.observer.GenerationStreamEmitter;
import com.lingmaforge.backend.model.Patch;
import com.lingmaforge.backend.service.ProjectFileService;

/**
 * @Tool 方法单元测试。
 *
 * <p>使用 Mockito 模拟 ProjectFileService 和 GenerationStreamEmitter，
 * 验证每个工具的输入参数处理、返回值格式和副作用（写文件、发 SSE）。</p>
 */
@DisplayName("@Tool 工具体系测试")
@ExtendWith(MockitoExtension.class)
class ToolUnitTest {

    @Mock
    private ProjectFileService projectFileService;

    @Mock
    private GenerationStreamEmitter emitter;

    private FileTools fileTools;
    private ProjectContextTools projectContextTools;
    private IterationTools iterationTools;

    private static final Long PROJECT_ID = 1L;
    private static final String TASK_ID = "test-task";

    @BeforeEach
    void setUp() {
        fileTools = new FileTools(projectFileService);
        projectContextTools = new ProjectContextTools(null, projectFileService); // ProjectService not needed for these tests
        iterationTools = new IterationTools(projectFileService);
        GenerationContext.set(PROJECT_ID, TASK_ID, emitter);
    }

    @AfterEach
    void tearDown() {
        GenerationContext.clear();
    }

    // ==================== writeFile ====================

    @Nested
    @DisplayName("writeFile 工具")
    class WriteFileTests {

        @Test
        @DisplayName("写入文件后应返回成功消息含行数")
        void shouldReturnSuccessWithLineCount() {
            String content = "import React from 'react';\n\nconst App = () => <div>Hello</div>;\n\nexport default App;";
            when(projectFileService.writeFile(anyLong(), anyString(), anyString(), eq("new")))
                    .thenReturn(4);

            String result = fileTools.writeFile("src/App.tsx", content);

            assertThat(result).contains("文件写入成功");
            assertThat(result).contains("src/App.tsx");
            assertThat(result).contains("4 行");
            verify(projectFileService).writeFile(PROJECT_ID, "src/App.tsx", content, "new");
        }

        @Test
        @DisplayName("写入文件后应推送 SSE file 事件")
        void shouldEmitFileEvent() {
            String content = "export default {}";
            when(projectFileService.writeFile(anyLong(), anyString(), anyString(), eq("new")))
                    .thenReturn(1);

            fileTools.writeFile("src/types.ts", content);

            verify(emitter).emitFile("src/types.ts", content, "new");
        }
    }

    // ==================== patchFile ====================

    @Nested
    @DisplayName("patchFile 工具")
    class PatchFileTests {

        @Test
        @DisplayName("应用补丁后应返回成功消息含修改数")
        void shouldApplyPatchesAndReturnCount() {
            List<Patch> patches = List.of(
                    new Patch(3, "color: red;", "color: blue;"),
                    new Patch(8, "margin: 0;", "margin: 16px;"));
            when(projectFileService.patchFile(anyLong(), anyString(), any()))
                    .thenReturn(2);
            String newContent = ":root { --color: blue; }";
            when(projectFileService.readFile(anyLong(), anyString()))
                    .thenReturn(newContent);

            String result = fileTools.patchFile("src/styles/globals.css", patches);

            assertThat(result).contains("2 处修改");
            assertThat(result).contains("src/styles/globals.css");
            verify(emitter).emitFile("src/styles/globals.css", newContent, "modified");
        }

        @Test
        @DisplayName("空补丁列表应返回未修改消息")
        void shouldReturnNoChangeForEmptyPatches() {
            String result = fileTools.patchFile("src/styles/globals.css", List.of());
            assertThat(result).contains("未提供补丁");
        }
    }

    // ==================== validateCode ====================

    @Nested
    @DisplayName("validateCode 工具")
    class ValidateCodeTests {

        @Test
        @DisplayName("代码验证通过时返回'验证通过'")
        void shouldPassValidation() {
            String code = """
                    import React from 'react';

                    interface Props {
                      name: string;
                      price: number;
                    }

                    const PlanCard = ({ name, price }: Props) => {
                      return <div>{name}</div>;
                    };

                    export default PlanCard;
                    """;
            when(projectFileService.listFilePaths(anyLong()))
                    .thenReturn(List.of());

            String result = fileTools.validateCode("src/components/PlanCard.tsx", code);

            assertThat(result).contains("验证通过");
        }

        @Test
        @DisplayName("检测到不存在的 import 路径时应列出错误")
        void shouldDetectMissingImport() {
            String code = """
                    import React from 'react';
                    import NonExistent from './NonExistentFile';
                    export default {};
                    """;
            when(projectFileService.listFilePaths(anyLong()))
                    .thenReturn(List.of("src/styles/globals.css")); // NonExistentFile NOT in list

            String result = fileTools.validateCode("src/components/Foo.tsx", code);

            assertThat(result).contains("验证失败");
            assertThat(result).contains("import 路径不存在");
        }

        @Test
        @DisplayName("检测到缺少 export 声明时应报告")
        void shouldDetectMissingExport() {
            String code = """
                    import React from 'react';
                    const App = () => <div>Hello</div>;
                    """;
            when(projectFileService.listFilePaths(anyLong())).thenReturn(List.of());

            String result = fileTools.validateCode("src/App.tsx", code);

            assertThat(result).contains("验证失败");
            assertThat(result).contains("缺少 export 声明");
        }

        @Test
        @DisplayName("检测到 any 类型时应报告")
        void shouldDetectAnyType() {
            String code = """
                    const data: any = fetchData();
                    export default data;
                    """;
            when(projectFileService.listFilePaths(anyLong())).thenReturn(List.of());

            String result = fileTools.validateCode("src/data.ts", code);

            assertThat(result).contains("验证失败");
            assertThat(result).contains("any 类型");
        }

        @Test
        @DisplayName("外部包（react、@等）不应被视为不存在的 import")
        void shouldAllowExternalPackages() {
            String code = """
                    import React, { useState } from 'react';
                    import ReactDOM from 'react-dom/client';
                    import axios from 'axios';
                    import { BrowserRouter } from 'react-router-dom';
                    import { useForm } from '@tanstack/react-form';
                    export default {};
                    """;
            when(projectFileService.listFilePaths(anyLong())).thenReturn(List.of());

            String result = fileTools.validateCode("src/App.tsx", code);

            // react、react-dom、axios、react-router-dom、@tanstack/* 都是外部包，不应报错
            assertThat(result).contains("验证通过");
        }
    }

    // ==================== readFileContext ====================

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

            String result = projectContextTools.readFileContext(
                    List.of("src/styles/globals.css", "src/components/PlanCard.tsx"));

            assertThat(result).contains("--- 文件: src/styles/globals.css ---");
            assertThat(result).contains(":root { --primary: blue; }");
            assertThat(result).contains("--- 文件: src/components/PlanCard.tsx ---");
            assertThat(result).contains("export default PlanCard;");
        }

        @Test
        @DisplayName("不存在的文件标注'尚未生成'")
        void shouldMarkMissingFiles() {
            when(projectFileService.readFiles(eq(PROJECT_ID), any()))
                    .thenReturn(Collections.singletonMap("src/App.tsx", null));

            String result = projectContextTools.readFileContext(List.of("src/App.tsx"));

            assertThat(result).contains("src/App.tsx");
            assertThat(result).contains("尚未生成");
        }
    }

    // ==================== searchCode ====================

    @Nested
    @DisplayName("searchCode 工具")
    class SearchCodeTests {

        @Test
        @DisplayName("找到关键词时返回匹配行及上下文")
        void shouldReturnMatchingLinesWithContext() {
            String fileContent = """
                    line 1
                    line 2
                    const price = 49;
                    line 4
                    line 5
                    """;
            when(projectFileService.readFile(PROJECT_ID, "src/api/mock.ts"))
                    .thenReturn(fileContent);

            String result = iterationTools.searchCode("price", List.of("src/api/mock.ts"));

            assertThat(result).contains("文件: src/api/mock.ts");
            assertThat(result).contains("行 3");
            assertThat(result).contains(">>> const price = 49;"); // 匹配行有 >>> 标记
            assertThat(result).contains("line 2"); // 上文
            assertThat(result).contains("line 4"); // 下文
        }

        @Test
        @DisplayName("未找到关键词时返回提示")
        void shouldReturnNotFoundMessage() {
            when(projectFileService.readFile(PROJECT_ID, "src/App.tsx"))
                    .thenReturn("export default App;");

            String result = iterationTools.searchCode("nonexistent", List.of("src/App.tsx"));

            assertThat(result).contains("未找到包含关键词的代码");
        }
    }
}
