package com.justdoit.task.feature.export;

import org.springframework.core.io.Resource;

public record ExportDownload(Resource resource, String fileName, String contentType) { }
