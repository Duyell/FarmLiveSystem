package com.farmlive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FarmLiveApplication {
    public static void main(String[] args) {
        SpringApplication.run(FarmLiveApplication.class, args);
    }
}
