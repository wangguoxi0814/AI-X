package com.aix.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.aix")
@MapperScan("com.aix.storage.mapper")
@ConfigurationPropertiesScan("com.aix")
public class AiXApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiXApplication.class, args);
    }
}
