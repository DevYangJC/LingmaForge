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
 * 项目聚合实体，对应数据库表 lf_project。
 *
 * <p>一个项目包含多个文件、触发多次生成任务、包含多条对话消息。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("lf_project")
public class ProjectEntity {

    /** 项目唯一标识，雪花算法分配。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 面向用户展示的项目名称，必填。 */
    private String name;

    /** 项目描述，可空。 */
    private String description;

    /** 框架类型，如 react-vite-ts，必填。 */
    private String framework;

    /** 当前项目生命周期状态：draft / generating / built / failed。 */
    private String status;

    /** 最近一次构建状态：PENDING / SUCCESS / FAILED，可空。 */
    private String lastBuildStatus;

    /** 沙箱预览 URL，可空。 */
    private String sandboxUrl;

    /** 创建时间，插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后更新时间，插入和更新时自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
