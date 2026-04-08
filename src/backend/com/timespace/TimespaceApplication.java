package com.timespace;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.timespace.module.*.mapper")
public class TimespaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimespaceApplication.class, args);
    }
}
