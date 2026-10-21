package com.nuist.agent;

import com.nuist.agent.zhangxu.config.JobDatabaseInitializer;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@MapperScan(basePackages = {"com.nuist.agent.mapper", "com.nuist.agent.zhangxu.mapper"})
public class JobAssistantApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(JobAssistantApplication.class);
        // 容器刷新前自动建库建表：新机器首次启动无需手工执行 sql/schema.sql
        application.addInitializers(new JobDatabaseInitializer());
        application.run(args);
    }
}
