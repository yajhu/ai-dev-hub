package com.aidevhub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aidevhub.mapper")
public class AiDevHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDevHubApplication.class, args);
    }
}
