package com.lingmaforge.backend.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 生成任务实体，对应数据库表 lf_generation_task。
 *
 * <p>任务 ID 即 StateGraph threadId 与 SSE streamId，用于流式中断恢复。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("lf_generation_task")
public class GenerationTaskEntity {

    /** 自增主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 任务 ID（= StateGraph threadId / SSE streamId），唯一。 */
    private String taskId;

    /** 所属项目 ID，关联 lf_project.id。 */
    private Long projectId;

    /** 任务类型：create（首次生成）/ iterate（迭代修改）。 */
    private String taskType;

    /** 用户输入的自然语言 prompt。 */
    private String prompt;

    /** 当前所处的阶段节点名称。 */
    private String currentStage;

    /** 任务状态：running / completed / failed / stopped。 */
    private String status;

    /** 构建耗时（秒）。 */
    private Integer buildTime;

    /** 预览 URL。 */
    private String previewUrl;

    /** 错误信息。 */
    private String errorMessage;

    /** 创建时间，插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后更新时间，插入和更新时自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
