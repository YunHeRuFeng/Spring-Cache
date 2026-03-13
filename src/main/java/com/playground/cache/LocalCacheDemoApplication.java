package com.playground.cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LocalCacheDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalCacheDemoApplication.class, args);
    }
}
