package com.justdoit.schedule.feature.schedule;

import com.justdoit.schedule.integration.TaskReportClient.TaskReport;
import com.justdoit.schedule.shared.TimeBlockResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyAnalyticsPayloadConverterTest {

    private final WeeklyAnalyticsPayloadConverter converter = new WeeklyAnalyticsPayloadConverter();

    @Test
    void preservaRelatorioDatasEBlocosNoRoundTripJson() {
        UUID blockId = UUID.randomUUID();
        LocalDate weekStart = LocalDate.of(2026, 8, 3);
        TaskReport report = new TaskReport(weekStart, weekStart.plusDays(6), 7, 5,
                3_600, 600, List.of(), 5, 5, 1, 0, 0, 0,
                3_600, 0, List.of(), List.of());
        TimeBlockResponse block = new TimeBlockResponse(blockId, UUID.randomUUID(), UUID.randomUUID(),
                weekStart.atTime(9, 0), weekStart.atTime(10, 0), 60, weekStart);
        WeeklyAnalyticsPayload original = new WeeklyAnalyticsPayload(
                WeeklyAnalyticsPayload.CURRENT_VERSION, report, List.of(block));

        WeeklyAnalyticsPayload restored = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(original));

        assertThat(restored).isEqualTo(original);
        assertThat(restored.timeBlocks().getFirst().startDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 3, 9, 0));
    }
}
