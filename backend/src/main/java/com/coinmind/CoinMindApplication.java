package com.coinmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CoinMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoinMindApplication.class, args);
    }
}
