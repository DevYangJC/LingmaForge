package com.lingmaforge.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lingmaforge.backend.entity.GenerationTaskEntity;
import com.lingmaforge.backend.mapper.GenerationTaskMapper;
import com.lingmaforge.backend.service.GenerationTaskService;
import org.springframework.stereotype.Service;

/**
 * 生成任务服务实现。
 */
@Service
public class GenerationTaskServiceImpl extends ServiceImpl<GenerationTaskMapper, GenerationTaskEntity>
        implements GenerationTaskService {

    @Override
    public GenerationTaskEntity createTask(Long projectId, String taskId, String type, String prompt) {
        GenerationTaskEntity task = new GenerationTaskEntity();
        task.setTaskId(taskId);
        task.setProjectId(projectId);
        task.setTaskType(type);
        task.setPrompt(prompt);
        task.setStatus("running");
        task.setCurrentStage("requirement_analysis");
        save(task);
        return task;
    }

    @Override
    public void updateStage(String taskId, String stage) {
        GenerationTaskEntity task = getByTaskId(taskId);
        if (task != null) {
            task.setCurrentStage(stage);
            updateById(task);
        }
    }

    @Override
    public void markCompleted(String taskId, String previewUrl, Integer buildTime) {
        GenerationTaskEntity task = getByTaskId(taskId);
        if (task != null) {
            task.setStatus("completed");
            task.setPreviewUrl(previewUrl);
            task.setBuildTime(buildTime);
            updateById(task);
        }
    }

    @Override
    public void markFailed(String taskId, String error) {
        GenerationTaskEntity task = getByTaskId(taskId);
        if (task != null) {
            task.setStatus("failed");
            task.setErrorMessage(truncate(error, 1000));
            updateById(task);
        }
    }

    @Override
    public void markStopped(String taskId) {
        GenerationTaskEntity task = getByTaskId(taskId);
        if (task != null) {
            task.setStatus("stopped");
            updateById(task);
        }
    }

    @Override
    public GenerationTaskEntity getByTaskId(String taskId) {
        return getOne(new LambdaQueryWrapper<GenerationTaskEntity>()
                .eq(GenerationTaskEntity::getTaskId, taskId));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
