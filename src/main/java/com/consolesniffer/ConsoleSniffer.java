package com.consolesniffer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ConsoleSniffer {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleSniffer.class, args);
    }
}
