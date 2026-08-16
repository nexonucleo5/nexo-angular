package com.nexo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NexoApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexoApplication.class, args);
    }
}
