package com.justdoit.task.shared;

/** Origem do tempo registrado, para separar medição real de estimativa inferida. */
public enum TimeEntrySource {
    TIMER,
    MANUAL,
    COMPLETION_ESTIMATE,
    LEGACY
}
