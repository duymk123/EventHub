package com.eventhub.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Main entry point for the Order Service.
 * <p>
 * The {@code @EnableJpaAuditing} annotation activates JPA Auditing so that
 * {@code @CreatedDate}, {@code @LastModifiedDate}, {@code @CreatedBy} and
 * {@code @LastModifiedBy} annotations on entity classes are processed.
 */
@SpringBootApplication
@EnableJpaAuditing
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
