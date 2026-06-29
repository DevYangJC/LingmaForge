package com.lingmaforge.backend.workbench.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lingmaforge.backend.common.api.Result;
import com.lingmaforge.backend.common.model.SandboxInfo;
import com.lingmaforge.backend.workbench.service.SandboxService;

/**
 * 沙箱管理相关的 REST 接口。
 */
@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    private final SandboxService sandboxService;

    public SandboxController(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    /**
     * 启动沙箱预览。
     *
     * @param projectId 项目 ID
     * @return 沙箱信息
     */
    @PostMapping("/{projectId}/start")
    public Result<SandboxInfo> start(@PathVariable Long projectId) {
        return Result.ok(sandboxService.startDevServer(projectId));
    }

    /**
     * 停止沙箱预览。
     *
     * @param projectId 项目 ID
     * @return 操作结果
     */
    @PostMapping("/{projectId}/stop")
    public Result<Void> stop(@PathVariable Long projectId) {
        sandboxService.stopDevServer(projectId);
        return Result.ok(null);
    }

    /**
     * 查询沙箱状态。
     *
     * @param projectId 项目 ID
     * @return 沙箱信息
     */
    @GetMapping("/{projectId}/status")
    public Result<SandboxInfo> status(@PathVariable Long projectId) {
        return Result.ok(sandboxService.getStatus(projectId));
    }
}
