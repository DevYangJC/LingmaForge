package com.lingmaforge.backend.workbench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("项目模板初始化服务")
@ExtendWith(MockitoExtension.class)
class ProjectTemplateServiceTest {

    @Mock
    private ProjectFileService projectFileService;

    @Test
    @DisplayName("通过项目文件服务初始化 vue-vite-ts 模板")
    void initializesVueViteTemplateThroughProjectFileService() {
        ProjectTemplateService service = new ProjectTemplateService(projectFileService);

        int count = service.initialize(42L, "vue-vite-ts");

        assertThat(count).isPositive();
        verify(projectFileService).writeFile(eq(42L), eq("package.json"), org.mockito.ArgumentMatchers.contains("\"vue\""), eq("unchanged"));
        verify(projectFileService).writeFile(eq(42L), eq("src/App.vue"), org.mockito.ArgumentMatchers.contains("<template>"), eq("unchanged"));
        verify(projectFileService).writeFile(eq(42L), eq("src/main.ts"), org.mockito.ArgumentMatchers.contains("createApp"), eq("unchanged"));
    }
}
