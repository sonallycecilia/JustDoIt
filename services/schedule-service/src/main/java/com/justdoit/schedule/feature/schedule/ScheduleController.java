package com.justdoit.schedule.feature.schedule;

import com.justdoit.schedule.shared.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping("/time-blocks")
    public ResponseEntity<TimeBlockResponse> createTimeBlock(@RequestBody @Valid TimeBlockRequest request,
                                                             @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.createTimeBlock(request, userId));
    }

    @GetMapping("/time-blocks")
    public ResponseEntity<List<TimeBlockResponse>> getTimeBlocks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UUID userId) {
        if (from != null && to != null) {
            return ResponseEntity.ok(scheduleService.getTimeBlocksBetween(from, to, userId));
        }
        if (date != null) {
            return ResponseEntity.ok(scheduleService.getTimeBlocksByDate(date, userId));
        }
        return ResponseEntity.badRequest().build();
    }

    @PutMapping("/time-blocks/{id}")
    public ResponseEntity<TimeBlockResponse> updateTimeBlock(@PathVariable UUID id,
                                                             @RequestBody @Valid TimeBlockRequest request,
                                                             @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(scheduleService.updateTimeBlock(id, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/time-blocks/{id}")
    public ResponseEntity<Void> deleteTimeBlock(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            scheduleService.deleteTimeBlock(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/weekly-plans")
    public ResponseEntity<WeeklyPlanResponse> createWeeklyPlan(@RequestBody @Valid WeeklyPlanRequest request,
                                                               @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.createWeeklyPlan(request, userId));
    }

    @PatchMapping("/weekly-plans/{id}/close")
    public ResponseEntity<WeeklyPlanResponse> closeWeeklyPlan(@PathVariable UUID id,
                                                              @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(scheduleService.closeWeeklyPlan(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/weekly-plans/{id}/summary")
    public ResponseEntity<WeeklySummaryResponse> generateWeeklySummary(@PathVariable UUID id,
                                                                       @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(scheduleService.generateWeeklySummary(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/weekly-plans/{id}/summary")
    public ResponseEntity<WeeklySummaryResponse> getWeeklySummary(@PathVariable UUID id,
                                                                  @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(scheduleService.generateWeeklySummary(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
