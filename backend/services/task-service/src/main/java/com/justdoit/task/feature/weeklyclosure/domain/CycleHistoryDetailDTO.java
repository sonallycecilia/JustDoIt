package com.justdoit.task.feature.weeklyclosure.domain;

import java.util.List;

public record CycleHistoryDetailDTO(
    WeeklyCycle cycle,
    List<WeeklyTaskSnapshot> tasks,
    List<WeeklyTimeEntrySnapshot> timeEntries
) {}