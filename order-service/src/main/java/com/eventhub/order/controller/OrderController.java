package com.eventhub.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple example REST controller for the Order Service.
 * <p>
 * This controller demonstrates a basic Spring Boot endpoint.
 * You can extend it with real business logic, service layer calls,
 * request/response DTOs, validation, etc.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /**
     * Health‑check endpoint that returns a friendly message.
     *
     * @return a simple greeting string
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello from Order Service!";
    }
}
