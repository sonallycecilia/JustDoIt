package com.justdoit.task.feature.cycle;

import com.justdoit.task.shared.CycleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CycleInstanceJobTest {

    @Mock private CycleConfigRepository cycleConfigRepository;
    @Mock private CycleMaterializer materializer;
    @InjectMocks private CycleInstanceJob job;

    private CycleConfig config() {
        return CycleConfig.builder()
                .id(UUID.randomUUID())
                .cycleType(CycleType.BIWEEKLY)
                .nextResetDate(LocalDate.now())
                .build();
    }

    @Test
    @DisplayName("materializa (repõe) cada série existente")
    void materializa_cada_serie() {
        CycleConfig c1 = config();
        CycleConfig c2 = config();
        when(cycleConfigRepository.findAllWithTask()).thenReturn(List.of(c1, c2));
        when(materializer.materialize(any())).thenReturn(1, 0);

        job.generateDueCycleInstances();

        verify(materializer).materialize(c1);
        verify(materializer).materialize(c2);
    }

    @Test
    @DisplayName("sem séries: não materializa")
    void sem_series() {
        when(cycleConfigRepository.findAllWithTask()).thenReturn(List.of());

        job.generateDueCycleInstances();

        verify(materializer, never()).materialize(any());
    }
}
