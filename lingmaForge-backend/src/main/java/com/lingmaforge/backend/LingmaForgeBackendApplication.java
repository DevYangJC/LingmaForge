package com.lingmaforge.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 灵码工坊后端的 Spring Boot 启动入口。
 *
 * <p>当前迭代聚焦 AI 对话核心闭环（{@code chat} + {@code workbench} 模块），
 * 因此通过 {@link ComponentScan} 的排除过滤器冻结以下空壳模块，避免它们注册空 Controller：
 * <ul>
 *   <li>{@code auth} —— 用户认证（预留骨架）</li>
 *   <li>{@code admin} —— 管理端（预留骨架）</li>
 *   <li>{@code billing} —— 订阅与计费（预留骨架）</li>
 *   <li>{@code creative} —— 创意中心（预留骨架）</li>
 *   <li>{@code doc} —— 文档中心（预留骨架）</li>
 * </ul>
 * 待对应功能正式实现时，从 {@link #FROZEN_MODULES} 中移除对应包名即可解冻。</p>
 */
@MapperScan("com.lingmaforge.backend.workbench.mapper")
@SpringBootApplication
@ComponentScan(
        basePackages = "com.lingmaforge.backend",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                        // 冻结的空壳模块：匹配各模块下所有类
                        "com\\.lingmaforge\\.backend\\.auth\\..*",
                        "com\\.lingmaforge\\.backend\\.admin\\..*",
                        "com\\.lingmaforge\\.backend\\.billing\\..*",
                        "com\\.lingmaforge\\.backend\\.creative\\..*",
                        "com\\.lingmaforge\\.backend\\.doc\\..*"
                }))
public class LingmaForgeBackendApplication {

    /**
     * 已冻结的空壳模块包名列表（文档用途，实际排除由 {@code @ComponentScan} 的过滤器完成）。
     * 解冻某模块时：从此列表移除 + 删除上面 excludeFilters 中对应行。
     */
    public static final String[] FROZEN_MODULES = {
            "auth", "admin", "billing", "creative", "doc"
    };

    public static void main(String[] args) {
        SpringApplication.run(LingmaForgeBackendApplication.class, args);
    }
}
