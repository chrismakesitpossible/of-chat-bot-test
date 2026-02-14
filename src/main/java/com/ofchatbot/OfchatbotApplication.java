package com.ofchatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OfchatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfchatbotApplication.class, args);
    }

}
