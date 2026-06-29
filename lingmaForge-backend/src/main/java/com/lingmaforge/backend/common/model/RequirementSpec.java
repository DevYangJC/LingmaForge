package com.lingmaforge.backend.common.model;

import java.util.List;
import java.util.Map;

/**
 * AI 分析生成的结构化应用需求。
 *
 * @param appName     应用名称
 * @param description 应用描述
 * @param pages       页面列表
 * @param apis        API 接口列表
 * @param features    功能特性列表
 * @param style       样式规格
 */
public record RequirementSpec(
        String appName,
        String description,
        List<PageSpec> pages,
        List<ApiSpec> apis,
        List<String> features,
        StyleSpec style) {

    /**
     * 页面规格。
     *
     * @param name        页面名称
     * @param route       页面路由
     * @param description 页面描述
     * @param components  页面包含的组件列表
     */
    public record PageSpec(String name, String route, String description, List<String> components) {
    }

    /**
     * API 接口规格。
     *
     * @param name         API 名称
     * @param path         请求路径
     * @param method       HTTP 方法（GET / POST / PUT / DELETE）
     * @param description  接口描述
     * @param requestShape 请求体结构（JSON Schema 风格）
     * @param responseShape 响应体结构（JSON Schema 风格）
     */
    public record ApiSpec(
            String name,
            String path,
            String method,
            String description,
            Map<String, Object> requestShape,
            Map<String, Object> responseShape) {
    }

    /**
     * 样式规格。
     *
     * @param theme     主题类型（light / dark / custom）
     * @param themeName 主题名称
     * @param layout    布局模式（single-page / multi-page / dashboard）
     * @param fontFamily 字体族
     */
    public record StyleSpec(String theme, String themeName, String layout, String fontFamily) {
    }
}
