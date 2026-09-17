package com.justdoit.task.feature.export;

import com.justdoit.task.shared.TaskExportResponse;

import java.util.List;

public record ExportPage(List<TaskExportResponse.TaskRow> rows, boolean hasNext) { }
