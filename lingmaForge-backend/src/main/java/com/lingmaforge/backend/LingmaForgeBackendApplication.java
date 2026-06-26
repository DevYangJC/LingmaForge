package com.lingmaforge.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.lingmaforge.backend.mapper")
@SpringBootApplication
/**
 * 灵码工坊后端的 Spring Boot 启动入口。
 */
public class LingmaForgeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingmaForgeBackendApplication.class, args);
    }
}
