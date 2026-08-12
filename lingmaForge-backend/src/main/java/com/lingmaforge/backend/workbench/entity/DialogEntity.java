package com.lingmaforge.backend.workbench.entity;

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
 * 对话会话实体，对应数据库表 lf_dialog。
 *
 * <p>一个会话包含多条 {@link ChatMessageEntity}。会话级维度比 taskId 更上层，
 * 支持同一项目下的多轮独立对话。Phase 1 只建表与实体，Phase 2 接口层填充使用。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("lf_dialog")
public class DialogEntity {

    /** 会话唯一标识（雪花 ID）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务会话 ID（= SSE streamId），UUID 去横线。 */
    private String dialogId;

    /** 关联项目 ID，可空（闲聊无项目）。 */
    private Long projectId;

    /** 会话标题，可空。 */
    private String title;

    /** 会话状态：active / archived。 */
    private String status;

    /** 创建时间，插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入与更新时自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
