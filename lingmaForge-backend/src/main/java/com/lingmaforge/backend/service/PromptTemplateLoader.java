package com.lingmaforge.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
/**
 * 从类路径资源加载并渲染提示词模板。
 */
public class PromptTemplateLoader {

    private final ResourceLoader resourceLoader;

    public PromptTemplateLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String loadSystemPrompt(String nodeName) {
        return loadPrompt("classpath:prompts/%s-system.txt".formatted(nodeName), Map.of());
    }

    public String loadUserPrompt(String nodeName, Map<String, String> variables) {
        return loadPrompt("classpath:prompts/%s-user.txt".formatted(nodeName), variables);
    }

    private String loadPrompt(String location, Map<String, String> variables) {
        Resource resource = resourceLoader.getResource(location);
        try {
            String template = resource.getContentAsString(StandardCharsets.UTF_8);
            String rendered = template;
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                rendered = rendered.replace("{{%s}}".formatted(entry.getKey()), entry.getValue());
            }
            return rendered;
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载提示词模板: " + location, exception);
        }
    }
}
