package com.lingmaforge.backend;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-key"
})
class LingmaForgeBackendApplicationTests {

    private static final Logger log = LoggerFactory.getLogger(LingmaForgeBackendApplicationTests.class);

    @Test
    void contextLoads() {
        log.info("========================================");
        log.info("  LingmaForge 后端 Spring 上下文加载测试");
        log.info("  Spring Boot 版本: 3.5.7");
        log.info("  数据库: H2 (内嵌文件模式)");
        log.info("  环境: 测试环境 (spring.ai.dashscope.api-key=test-key)");
        log.info("  期望: Spring 上下文成功加载，所有 Bean 初始化无异常");
        log.info("========================================");
    }
}
