-- 灵码工坊核心表结构（H2 MySQL 模式 / MySQL 8 通用）
-- 一个项目包含多个文件、触发多次生成任务、包含多条对话消息。

CREATE TABLE IF NOT EXISTS lf_project (
    id              BIGINT PRIMARY KEY,
    name            VARCHAR(80)  NOT NULL,
    description     VARCHAR(500),
    framework       VARCHAR(32)  NOT NULL DEFAULT 'react-vite-ts',
    status          VARCHAR(32)  NOT NULL DEFAULT 'draft',
    last_build_status VARCHAR(32),
    sandbox_url     VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

-- 项目文件表：磁盘文件 + 数据库记录双写，数据库用于前端展示与版本管理
CREATE TABLE IF NOT EXISTS lf_project_file (
    id              BIGINT PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    path            VARCHAR(500) NOT NULL,
    file_type       VARCHAR(32),
    status          VARCHAR(32)  NOT NULL DEFAULT 'new',
    content         CLOB,
    checksum        VARCHAR(64),
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_project_file_path UNIQUE (project_id, path)
);

CREATE INDEX IF NOT EXISTS idx_project_file_project ON lf_project_file (project_id);

-- 生成任务表：任务 ID 即 StateGraph threadId / SSE streamId
CREATE TABLE IF NOT EXISTS lf_generation_task (
    id              BIGINT PRIMARY KEY,
    task_id         VARCHAR(64)  NOT NULL,
    project_id      BIGINT       NOT NULL,
    task_type       VARCHAR(16)  NOT NULL DEFAULT 'create',
    prompt          CLOB,
    current_stage   VARCHAR(32),
    status          VARCHAR(16)  NOT NULL DEFAULT 'running',
    build_time      INT,
    preview_url     VARCHAR(255),
    error_message   VARCHAR(1000),
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_generation_task_id UNIQUE (task_id)
);

CREATE INDEX IF NOT EXISTS idx_generation_task_project ON lf_generation_task (project_id);

-- 对话消息表：保存用户与助手的多轮对话
CREATE TABLE IF NOT EXISTS lf_chat_message (
    id              BIGINT PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    task_id         VARCHAR(64),
    role            VARCHAR(16)  NOT NULL,
    content         CLOB         NOT NULL,
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_message_project ON lf_chat_message (project_id);
