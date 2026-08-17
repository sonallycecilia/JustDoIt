package com.justdoit.task.feature.export;

public class ExportLimitExceededException extends RuntimeException {
    public ExportLimitExceededException(String message) {
        super(message);
    }
}
