package com.justdoit.schedule.feature.schedule;

import com.justdoit.schedule.shared.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final TimeBlockRepository timeBlockRepository;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklySummaryRepository weeklySummaryRepository;

    @Transactional
    public TimeBlockResponse createTimeBlock(TimeBlockRequest request, UUID userId) {
        TimeBlock block = TimeBlock.builder()
                .userId(userId)
                .taskId(request.taskId())
                .startDateTime(request.startDateTime())
                .endDateTime(request.endDateTime())
                .estimatedMinutes(request.estimatedMinutes())
                .date(request.date())
                .build();
        return toTimeBlockResponse(timeBlockRepository.save(block));
    }

    public List<TimeBlockResponse> getTimeBlocksByDate(LocalDate date, UUID userId) {
        return timeBlockRepository.findByUserIdAndDate(userId, date).stream()
                .map(this::toTimeBlockResponse)
                .toList();
    }

    public List<TimeBlockResponse> getTimeBlocksBetween(LocalDate from, LocalDate to, UUID userId) {
        return timeBlockRepository.findByUserIdAndDateBetween(userId, from, to).stream()
                .map(this::toTimeBlockResponse)
                .toList();
    }

    @Transactional
    public TimeBlockResponse updateTimeBlock(UUID blockId, TimeBlockRequest request, UUID userId) {
        TimeBlock block = timeBlockRepository.findByIdAndUserId(blockId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Time block not found"));
        block.setTaskId(request.taskId());
        block.setStartDateTime(request.startDateTime());
        block.setEndDateTime(request.endDateTime());
        block.setEstimatedMinutes(request.estimatedMinutes());
        block.setDate(request.date());
        return toTimeBlockResponse(timeBlockRepository.save(block));
    }

    @Transactional
    public void deleteTimeBlock(UUID blockId, UUID userId) {
        TimeBlock block = timeBlockRepository.findByIdAndUserId(blockId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Time block not found"));
        timeBlockRepository.delete(block);
    }

    @Transactional
    public WeeklyPlanResponse createWeeklyPlan(WeeklyPlanRequest request, UUID userId) {
        WeeklyPlan plan = WeeklyPlan.builder()
                .userId(userId)
                .weekStartDate(request.weekStartDate())
                .weekEndDate(request.weekEndDate())
                .status(ScheduleStatus.OPEN)
                .build();
        return toWeeklyPlanResponse(weeklyPlanRepository.save(plan));
    }

    @Transactional
    public WeeklyPlanResponse closeWeeklyPlan(UUID planId, UUID userId) {
        WeeklyPlan plan = weeklyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Weekly plan not found"));
        plan.setStatus(ScheduleStatus.CLOSED);
        return toWeeklyPlanResponse(weeklyPlanRepository.save(plan));
    }

    @Transactional
    public WeeklySummaryResponse generateWeeklySummary(UUID planId, UUID userId) {
        WeeklyPlan plan = weeklyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Weekly plan not found"));

        List<TimeBlock> blocks = timeBlockRepository.findByUserIdAndDateBetween(
                userId, plan.getWeekStartDate(), plan.getWeekEndDate()
        );

        int totalEstimated = blocks.stream()
                .mapToInt(b -> b.getEstimatedMinutes() != null ? b.getEstimatedMinutes() : 0)
                .sum();

        WeeklySummary summary = weeklySummaryRepository.findByWeeklyPlanId(planId)
                .orElse(WeeklySummary.builder().weeklyPlan(plan).build());

        summary.setTotalEstimatedMinutes(totalEstimated);
        summary.setTotalTasks(blocks.size());

        return toWeeklySummaryResponse(weeklySummaryRepository.save(summary));
    }

    public boolean overlaps(TimeBlock a, TimeBlock b) {
        return a.getStartDateTime().isBefore(b.getEndDateTime())
                && b.getStartDateTime().isBefore(a.getEndDateTime());
    }

    private TimeBlockResponse toTimeBlockResponse(TimeBlock b) {
        return new TimeBlockResponse(b.getId(), b.getUserId(), b.getTaskId(),
                b.getStartDateTime(), b.getEndDateTime(), b.getEstimatedMinutes(), b.getDate());
    }

    private WeeklyPlanResponse toWeeklyPlanResponse(WeeklyPlan p) {
        return new WeeklyPlanResponse(p.getId(), p.getUserId(),
                p.getWeekStartDate(), p.getWeekEndDate(), p.getStatus());
    }

    private WeeklySummaryResponse toWeeklySummaryResponse(WeeklySummary s) {
        return new WeeklySummaryResponse(s.getId(), s.getWeeklyPlan().getId(),
                s.getTotalEstimatedMinutes(), s.getTotalActualSeconds(),
                s.getDeviationSeconds(), s.getCompletedTasks(), s.getTotalTasks());
    }
}
