package com.justdoit.schedule.feature.schedule;

import com.justdoit.schedule.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock private TimeBlockRepository timeBlockRepository;
    @Mock private WeeklyPlanRepository weeklyPlanRepository;
    @Mock private WeeklySummaryRepository weeklySummaryRepository;
    @InjectMocks private ScheduleService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BLOCK_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final LocalDate TODAY = LocalDate.of(2026, 6, 21);
    private final LocalDateTime START = LocalDateTime.of(2026, 6, 21, 9, 0);
    private final LocalDateTime END   = LocalDateTime.of(2026, 6, 21, 10, 0);

    private TimeBlock timeBlock;
    private WeeklyPlan weeklyPlan;

    @BeforeEach
    void setUp() {
        timeBlock = TimeBlock.builder()
                .id(BLOCK_ID).userId(USER_ID)
                .startDateTime(START).endDateTime(END)
                .estimatedMinutes(60).date(TODAY)
                .build();

        weeklyPlan = WeeklyPlan.builder()
                .id(PLAN_ID).userId(USER_ID)
                .weekStartDate(TODAY).weekEndDate(TODAY.plusDays(6))
                .status(ScheduleStatus.OPEN)
                .build();
    }

    @Test
    void createTimeBlock_savesAndReturnsResponse() {
        TimeBlockRequest request = new TimeBlockRequest(null, START, END, 60, TODAY);
        when(timeBlockRepository.save(any())).thenReturn(timeBlock);

        TimeBlockResponse result = service.createTimeBlock(request, USER_ID);

        assertEquals(BLOCK_ID, result.id());
        assertEquals(USER_ID, result.userId());
        assertEquals(60, result.estimatedMinutes());
        verify(timeBlockRepository).save(any(TimeBlock.class));
    }

    @Test
    void getTimeBlocksByDate_returnsList() {
        when(timeBlockRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(List.of(timeBlock));

        List<TimeBlockResponse> result = service.getTimeBlocksByDate(TODAY, USER_ID);

        assertEquals(1, result.size());
        assertEquals(BLOCK_ID, result.get(0).id());
        assertEquals(TODAY, result.get(0).date());
    }

    @Test
    void createWeeklyPlan_savesAndReturnsResponse() {
        WeeklyPlanRequest request = new WeeklyPlanRequest(TODAY, TODAY.plusDays(6));
        when(weeklyPlanRepository.save(any())).thenReturn(weeklyPlan);

        WeeklyPlanResponse result = service.createWeeklyPlan(request, USER_ID);

        assertEquals(PLAN_ID, result.id());
        assertEquals(ScheduleStatus.OPEN, result.status());
        verify(weeklyPlanRepository).save(any(WeeklyPlan.class));
    }

    @Test
    void closeWeeklyPlan_setsStatusClosed() {
        WeeklyPlan closed = WeeklyPlan.builder()
                .id(PLAN_ID).userId(USER_ID)
                .weekStartDate(TODAY).weekEndDate(TODAY.plusDays(6))
                .status(ScheduleStatus.CLOSED)
                .build();
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(weeklyPlan));
        when(weeklyPlanRepository.save(any())).thenReturn(closed);

        WeeklyPlanResponse result = service.closeWeeklyPlan(PLAN_ID, USER_ID);

        assertEquals(ScheduleStatus.CLOSED, result.status());
    }

    @Test
    void closeWeeklyPlan_notFound_throwsException() {
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.closeWeeklyPlan(PLAN_ID, USER_ID));
    }

    @Test
    void generateWeeklySummary_calculatesTotals() {
        WeeklySummary summary = WeeklySummary.builder()
                .id(UUID.randomUUID()).weeklyPlan(weeklyPlan)
                .totalEstimatedMinutes(60).totalTasks(1)
                .build();
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(weeklyPlan));
        when(timeBlockRepository.findByUserIdAndDateBetween(USER_ID, TODAY, TODAY.plusDays(6)))
                .thenReturn(List.of(timeBlock));
        when(weeklySummaryRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(weeklySummaryRepository.save(any())).thenReturn(summary);

        WeeklySummaryResponse result = service.generateWeeklySummary(PLAN_ID, USER_ID);

        assertEquals(60, result.totalEstimatedMinutes());
        assertEquals(1, result.totalTasks());
    }

    @Test
    void generateWeeklySummary_planNotFound_throwsException() {
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.generateWeeklySummary(PLAN_ID, USER_ID));
    }

    @Test
    void overlaps_whenBlocksOverlap_returnsTrue() {
        TimeBlock a = TimeBlock.builder().startDateTime(START).endDateTime(END).build();
        TimeBlock b = TimeBlock.builder()
                .startDateTime(START.plusMinutes(30)).endDateTime(END.plusMinutes(30))
                .build();

        assertTrue(service.overlaps(a, b));
    }

    @Test
    void overlaps_whenBlocksDoNotOverlap_returnsFalse() {
        TimeBlock a = TimeBlock.builder().startDateTime(START).endDateTime(END).build();
        TimeBlock b = TimeBlock.builder()
                .startDateTime(END).endDateTime(END.plusHours(1))
                .build();

        assertFalse(service.overlaps(a, b));
    }
}
