// src/main/java/com/justdoit/schedule/ScheduleServiceApplication.java
package com.justdoit.schedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = { "com.justdoit.schedule", "com.justdoit.common" },
        exclude = { UserDetailsServiceAutoConfiguration.class })
public class ScheduleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScheduleServiceApplication.class, args);
    }
}
