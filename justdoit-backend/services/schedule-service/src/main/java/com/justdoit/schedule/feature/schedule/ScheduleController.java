package com.justdoit.schedule.feature.schedule;

import com.justdoit.schedule.shared.*;
import com.justdoit.schedule.config.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final JwtUtil jwtUtil;

    @PostMapping("/time-blocks")
    public ResponseEntity<TimeBlockResponse> createTimeBlock(@RequestBody @Valid TimeBlockRequest request,
                                                             HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.createTimeBlock(request, userId));
    }

    @GetMapping("/time-blocks")
    public ResponseEntity<List<TimeBlockResponse>> getTimeBlocksByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        return ResponseEntity.ok(scheduleService.getTimeBlocksByDate(date, userId));
    }

    @PostMapping("/weekly-plans")
    public ResponseEntity<WeeklyPlanResponse> createWeeklyPlan(@RequestBody @Valid WeeklyPlanRequest request,
                                                               HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.createWeeklyPlan(request, userId));
    }

    @PatchMapping("/weekly-plans/{id}/close")
    public ResponseEntity<WeeklyPlanResponse> closeWeeklyPlan(@PathVariable UUID id,
                                                              HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(scheduleService.closeWeeklyPlan(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/weekly-plans/{id}/summary")
    public ResponseEntity<WeeklySummaryResponse> generateWeeklySummary(@PathVariable UUID id,
                                                                       HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(scheduleService.generateWeeklySummary(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/weekly-plans/{id}/summary")
    public ResponseEntity<WeeklySummaryResponse> getWeeklySummary(@PathVariable UUID id,
                                                                  HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(scheduleService.generateWeeklySummary(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private UUID extractUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.extractUserId(token);
    }
}
