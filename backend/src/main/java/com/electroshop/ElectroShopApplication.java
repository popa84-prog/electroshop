package com.electroshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling powers NotificationService's periodic low-stock/no-image/
// inactive sweep (feature #8 — notificări automate).
@SpringBootApplication
@EnableScheduling
public class ElectroShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElectroShopApplication.class, args);
    }
}
