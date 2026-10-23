package com.signasource.signa_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SignaApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SignaApiApplication.class, args);
    }
}
