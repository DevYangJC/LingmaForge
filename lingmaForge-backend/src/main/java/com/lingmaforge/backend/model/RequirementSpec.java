package com.lingmaforge.backend.model;

import java.util.List;
import java.util.Map;

/**
 * AI 分析生成的结构化应用需求。
 */
public record RequirementSpec(
        String appName,
        String description,
        List<PageSpec> pages,
        List<ApiSpec> apis,
        List<String> features,
        StyleSpec style) {

    public record PageSpec(String name, String route, String description, List<String> components) {
    }

    public record ApiSpec(
            String name,
            String path,
            String method,
            String description,
            Map<String, Object> requestShape,
            Map<String, Object> responseShape) {
    }

    public record StyleSpec(String theme, String themeName, String layout, String fontFamily) {
    }
}
