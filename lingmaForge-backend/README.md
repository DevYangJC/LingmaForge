# lingmaForge-backend

灵码工坊后端服务，基于 JDK 21、Spring Boot、LangChain4j、LangGraph4j 和 MyBatis-Plus 初始化。

## 环境要求

- JDK 21
- Maven 3.9+
- 可选：MySQL 8；默认配置使用 H2 便于本地启动

## 启动

```bash
mvn spring-boot:run
```

OpenAI-compatible API Key 通过环境变量注入，默认可用于 DashScope 兼容模式：

```bash
set AI_DASHSCOPE_API_KEY=your-api-key`r`nset AI_OPENAI_COMPATIBLE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1`r`nset AI_CHAT_MODEL=qwen-plus
```

## 目录约定

- `web`: REST/SSE 控制器
- `service`: 业务服务
- `mapper`: MyBatis-Plus Mapper
- `entity`: 数据库实体
- `model`: DTO 和通用响应模型
- `ai`: 代码生成流水线、Agent、工具、观察者等扩展点
- `resources/prompts`: 提示词模板
