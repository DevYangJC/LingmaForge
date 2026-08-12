package com.lingmaforge.backend.auth.web;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证 Controller（预留骨架）。
 *
 * <p><b>已冻结</b>：当前迭代聚焦 AI 对话核心闭环（chat + workbench 模块），
 * 本空壳 Controller 已被启动类的 {@code @ComponentScan} 排除，不会被 Spring 加载。
 * 待后续实现登录鉴权时再解冻。详见 {@code chat} 模块的 DialogRouter。</p>
 *
 * @deprecated 已冻结，不在当前 ComponentScan 范围内。
 */
@Deprecated(forRemoval = false)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
}
