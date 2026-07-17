// src/main/java/com/justdoit/notification/NotificationServiceApplication.java
package com.justdoit.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = { "com.justdoit.notification", "com.justdoit.common" },
        exclude = { UserDetailsServiceAutoConfiguration.class })
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
