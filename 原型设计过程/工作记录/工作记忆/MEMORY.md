# LingmaForge 项目长期记忆

## 项目定位
- 产品名: 灵码工坊 (LingmaForge)
- 定位: 企业级 AI 应用生成平台（对话即开发），对标 Bolt.new / v0 / Lovable
- 核心闭环: 用户输入需求 → AI生成代码 → 实时预览 → 迭代修改 → 部署上线
- 差异化: 创意中心模板市场 + 面向中文用户

## 技术栈
- 前端: Vue 3 + Vite 8 + TypeScript 6 + Vue Router 5 + Pinia 3 + Monaco Editor + splitpanes + markdown-it + axios
- 后端: Spring Boot 3.x + Spring AI Alibaba 1.1+ (统一接入+编排) + MyBatis-Plus + MySQL 8.0 + Redis 7 + MinIO + Docker
- AI: Claude 3.5 Sonnet (代码生成主力) + DeepSeek-V3 (辅助/分析) + Qwen (备选)
- 不引入UI组件库，从原型CSS提取Design Token自建组件系统

## 项目当前状态
- M0 工程化地基: 前端Vue骨架已建立，7/9页面迁移完成，ProfileView/WorksView仅为占位
- 后端: 不存在（仅设计文档）
- AI功能: 全部前端模拟动画，无真实AI服务
- 无HTTP客户端、无API层、无认证系统

## 核心架构决策
- AI编排: Spring AI Alibaba Graph + ReactAgent (一个框架完成接入+编排)
- 流式推送: SSE (生成进度) + WebSocket (沙箱日志)
- 沙箱: Docker容器 + Nginx反向代理 + Vite Dev Server
- 生成流水线: 6阶段(需求分析→执行规划→代码生成→样式优化→构建验证→预览部署)
- 文件版本: Git-like diff存储
- 结构化输出: Spring AI `.entity()` 方法（不手动解析JSON，格式定义单一来源为Java类）
- 框架降级: 预留编排接口抽象层,可通过配置切换到LangGraph4j

## 里程碑规划
- M0: 工程化地基 (已完成)
- M1: 对话骨架 → M2: 代码生成+预览 → M3: 文件树+编辑器 → M4: 迭代修改 → M5: 持久化+用户系统 → M6: 部署上线 → M7: 支撑页面

## 关键文档
- outputs/灵码工坊-产品需求文档PRD.md (38.9KB)
- outputs/灵码工坊-登录方案设计.md (93.1KB, 15章)
- outputs/灵码工坊-AI代码生成核心功能架构方案.md (v3.0, 教学首篇, 已修订AI框架选型)
- outputs/灵码工坊-AI框架技术选型深度对比.md (四方案对比 + 最终推荐)
- outputs/灵码工坊-后端核心功能实现指南.md (v2.0, 采用.entity()结构化输出)
