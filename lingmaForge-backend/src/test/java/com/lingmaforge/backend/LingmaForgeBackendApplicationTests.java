package com.lingmaforge.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.dashscope.api-key=test-key"
})
class LingmaForgeBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
