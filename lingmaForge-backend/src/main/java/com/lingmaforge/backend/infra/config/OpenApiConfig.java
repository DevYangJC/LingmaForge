package com.lingmaforge.backend.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * SpringDoc OpenAPI 3.0 文档配置。
 *
 * <p>启动后访问 <a href="http://localhost:8081/swagger-ui.html">Swagger UI</a>
 * 或 <a href="http://localhost:8081/v3/api-docs">OpenAPI JSON</a>。</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lingmaForgeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("灵码工坊 (LingmaForge) API")
                        .description("""
                                灵码工坊后端 REST / SSE 接口文档。

                                ## 接口分类
                                - **项目管理**：项目 CRUD、文件树、文件读写
                                - **代码生成**：创建生成任务、SSE 流式进度、迭代修改、停止生成
                                - **沙箱管理**：启动 / 停止 / 查询沙箱预览
                                - **健康检查**：服务就绪探针
                                """)
                        .version("0.0.1")
                        .contact(new Contact()
                                .name("LingmaForge Team"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
