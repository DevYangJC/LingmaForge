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
 * 项目文件实体，对应数据库表 lf_project_file。
 *
 * <p>磁盘文件用于构建运行，数据库记录用于前端展示与版本管理，两者双写保持一致。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("lf_project_file")
public class ProjectFileEntity {

    /** 文件记录唯一标识。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属项目 ID，关联 lf_project.id。 */
    private Long projectId;

    /** 文件相对项目根目录的路径，如 src/components/PlanCard.tsx，必填。 */
    private String path;

    /** 文件类型：config / style / api / component / page / entry 等。 */
    private String fileType;

    /** 文件状态：new / modified / unchanged。 */
    private String status;

    /** 文件内容。 */
    private String content;

    /** 文件内容校验和（SHA-256），用于变更检测。 */
    private String checksum;

    /** 创建时间，插入时自动填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最后更新时间，插入和更新时自动填充。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
