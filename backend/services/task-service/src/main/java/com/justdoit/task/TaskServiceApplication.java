// src/main/java/com/justdoit/task/TaskServiceApplication.java
package com.justdoit.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // job horário de tarefas atrasadas (OverdueTaskJob)
@SpringBootApplication(
        scanBasePackages = { "com.justdoit.task", "com.justdoit.common" },
        exclude = { UserDetailsServiceAutoConfiguration.class })
public class TaskServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskServiceApplication.class, args);
    }
}
