package com.eventhub.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        System.out.println("Dang chay AuthServiceApplication nè ...");
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
