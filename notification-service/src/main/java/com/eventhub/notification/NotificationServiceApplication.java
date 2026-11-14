package com.eventhub.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi động Notification Service. Spring Boot quét các package con để đăng ký controller, service,
 * repository, cấu hình security và consumer Kafka.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
