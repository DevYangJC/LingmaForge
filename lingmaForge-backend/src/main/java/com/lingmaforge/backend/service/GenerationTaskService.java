package com.lingmaforge.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lingmaforge.backend.entity.GenerationTaskEntity;

/**
 * 生成任务领域服务契约。
 *
 * <p>负责任务的创建、状态流转与查询。任务 ID 即 StateGraph threadId 与 SSE streamId。</p>
 */
public interface GenerationTaskService extends IService<GenerationTaskEntity> {

    /**
     * 创建生成任务。
     *
     * @param projectId 项目 ID
     * @param taskId    任务 ID
     * @param type      任务类型：create / iterate
     * @param prompt    用户输入
     * @return 任务实体
     */
    GenerationTaskEntity createTask(Long projectId, String taskId, String type, String prompt);

    /**
     * 更新任务当前阶段。
     *
     * @param taskId 任务 ID
     * @param stage  当前阶段节点名
     */
    void updateStage(String taskId, String stage);

    /**
     * 标记任务完成。
     *
     * @param taskId     任务 ID
     * @param previewUrl 预览 URL
     * @param buildTime  构建耗时（秒）
     */
    void markCompleted(String taskId, String previewUrl, Integer buildTime);

    /**
     * 标记任务失败。
     *
     * @param taskId 任务 ID
     * @param error  错误信息
     */
    void markFailed(String taskId, String error);

    /**
     * 标记任务已停止。
     *
     * @param taskId 任务 ID
     */
    void markStopped(String taskId);

    /**
     * 根据任务 ID 查询任务。
     *
     * @param taskId 任务 ID
     * @return 任务实体，不存在返回 null
     */
    GenerationTaskEntity getByTaskId(String taskId);
}
