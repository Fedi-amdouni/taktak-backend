package com.taktak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaktakApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaktakApplication.class, args);
    }
}
