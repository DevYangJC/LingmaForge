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
 * 对话消息实体，对应数据库表 lf_chat_message。
 *
 * <p>保存用户与助手在某个项目下的多轮对话内容。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("lf_chat_message")
public class ChatMessageEntity {

    /** 消息唯一标识。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属项目 ID，关联 lf_project.id。 */
    private Long projectId;

    /** 关联的生成任务 ID，可空。 */
    private String taskId;

    /** 消息角色：user / assistant。 */
    private String role;

    /** 消息内容。 */
    private String content;

    /** 创建时间，插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
