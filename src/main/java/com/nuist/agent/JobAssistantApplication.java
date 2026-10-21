package com.nuist.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@MapperScan(basePackages = "com.nuist.agent.mapper")
public class JobAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobAssistantApplication.class, args);
    }
}
