package com.justdoit.task.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BiologicalCeilingProperties {

    @Value("${app.biological-ceiling.sleep-minutes:480}")
    private int sleepMinutes;

    public int getSleepMinutes() {
        return sleepMinutes;
    }
}