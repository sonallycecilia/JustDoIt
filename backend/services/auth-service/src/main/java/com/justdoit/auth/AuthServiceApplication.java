// src/main/java/com/justdoit/auth/AuthServiceApplication.java
package com.justdoit.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // limpeza periódica de refresh tokens expirados (RefreshTokenCleanupJob)
@SpringBootApplication(
        scanBasePackages = { "com.justdoit.auth", "com.justdoit.common" },
        exclude = { UserDetailsServiceAutoConfiguration.class })
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
