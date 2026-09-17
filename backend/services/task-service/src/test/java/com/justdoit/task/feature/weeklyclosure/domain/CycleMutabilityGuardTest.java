package com.justdoit.task.feature.weeklyclosure.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleMutabilityGuardTest {

    @Mock
    private WeeklyCycleRepository cycleRepository;

    private CycleMutabilityGuard guard;

    @BeforeEach
    void setUp() {
        guard = new CycleMutabilityGuard(cycleRepository);
    }

    @Test
    void danglingCycleReference_doesNotTurnExistingTaskInto404() {
        UUID missingCycleId = UUID.randomUUID();
        when(cycleRepository.findById(missingCycleId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> guard.ensureTaskIsMutable(missingCycleId));
    }

    @Test
    void closedCycle_remainsImmutable() {
        UUID cycleId = UUID.randomUUID();
        WeeklyCycle closed = cycle(cycleId, CycleStatus.CLOSED);
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(closed));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> guard.ensureTaskIsMutable(cycleId)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void openCycle_allowsMutation() {
        UUID cycleId = UUID.randomUUID();
        when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(cycle(cycleId, CycleStatus.OPEN)));

        assertDoesNotThrow(() -> guard.ensureTaskIsMutable(cycleId));
    }

    private WeeklyCycle cycle(UUID id, CycleStatus status) {
        LocalDateTime start = LocalDateTime.now();
        return new WeeklyCycle(id, UUID.randomUUID(), start, start.plusDays(7), status, start, start);
    }
}
