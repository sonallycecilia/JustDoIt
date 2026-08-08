package com.justdoit.schedule.feature.schedule;

import com.justdoit.schedule.shared.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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

    /**
     * Abre o plano da semana. Idempotente: se a semana já tem plano, devolve o
     * existente com 200 em vez de criar um segundo (201 só na criação de fato).
     */
    @PostMapping("/weekly-plans")
    public ResponseEntity<WeeklyPlanResponse> createWeeklyPlan(@RequestBody @Valid WeeklyPlanRequest request,
                                                               @AuthenticationPrincipal UUID userId) {
        boolean jaExistia = scheduleService.findWeeklyPlan(request.weekStartDate(), userId).isPresent();
        WeeklyPlanResponse plano = scheduleService.createWeeklyPlan(request, userId);
        return ResponseEntity.status(jaExistia ? HttpStatus.OK : HttpStatus.CREATED).body(plano);
    }

    /** Plano de uma semana pela data de início. 404 quando a semana ainda não foi aberta. */
    @GetMapping("/weekly-plans")
    public ResponseEntity<WeeklyPlanResponse> getWeeklyPlan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal UUID userId) {
        return scheduleService.findWeeklyPlan(weekStart, userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/weekly-plans/history")
    public ResponseEntity<List<WeeklyPlanResponse>> getWeeklyPlanHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(scheduleService.findWeeklyPlans(from, to, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/analytics/overall")
    public ResponseEntity<AnalyticsOverallResponse> getOverallAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UUID userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        try {
            return ResponseEntity.ok(scheduleService.getOverallAnalytics(from, to, userId, authHeader));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @PatchMapping("/weekly-plans/{id}/close")
    public ResponseEntity<WeeklyPlanResponse> closeWeeklyPlan(@PathVariable UUID id,
                                                              @AuthenticationPrincipal UUID userId,
                                                              @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        try {
            return ResponseEntity.ok(scheduleService.closeWeeklyPlan(id, userId, authHeader));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Calcula (ou recalcula) o resumo. Numa semana fechada devolve o retrato congelado. */
    @PostMapping("/weekly-plans/{id}/summary")
    public ResponseEntity<WeeklySummaryResponse> generateWeeklySummary(@PathVariable UUID id,
                                                                       @AuthenticationPrincipal UUID userId,
                                                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        try {
            return ResponseEntity.ok(scheduleService.generateWeeklySummary(id, userId, authHeader));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Lê o resumo salvo, sem recalcular. Antes este GET chamava o gerador, ou
     * seja, escrevia no banco (e ainda batia no task-service) a cada leitura.
     * 404 quando a semana ainda não tem resumo — quem quer gerar usa o POST.
     */
    @GetMapping("/weekly-plans/{id}/summary")
    public ResponseEntity<WeeklySummaryResponse> getWeeklySummary(@PathVariable UUID id,
                                                                  @AuthenticationPrincipal UUID userId) {
        try {
            return scheduleService.findWeeklySummary(id, userId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
