package com.lingmaforge.backend.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lingmaforge.backend.common.api.Result;

/**
 * 健康检查接口，用于后端就绪探针。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 返回服务运行状态。
     *
     * @return 服务状态信息
     */
    @GetMapping
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "service", "lingmaForge-backend",
                "status", "UP",
                "timestamp", Instant.now().toString()));
    }
}
