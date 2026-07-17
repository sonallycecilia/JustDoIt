package com.justdoit.task.feature.cycle;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;

import com.justdoit.task.shared.CycleType;
import com.justdoit.task.shared.IntervalUnit;
import com.justdoit.task.shared.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CycleMaterializerTest {

    @Mock private TaskRepository taskRepository;
    @Mock private CycleConfigRepository cycleConfigRepository;
    @InjectMocks private CycleMaterializer materializer;

    private static final UUID SERIE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private CycleConfig config(CycleType type, LocalDate dueDate, LocalDate endDate) {
        Task modelo = Task.builder()
                .id(SERIE).userId(UUID.randomUUID())
                .title("Reunião").description("time")
                .dueDate(dueDate).dueTime(LocalTime.of(9, 0))
                .status(TaskStatus.PENDING)
                .build();
        return CycleConfig.builder()
                .id(UUID.randomUUID()).task(modelo)
                .cycleType(type).endDate(endDate)
                .build();
    }

    @Test
    @DisplayName("sem ocorrências futuras: gera exatamente MAX_FUTURAS, com data/hora/série")
    void gera_ate_o_limite() {
        LocalDate base = LocalDate.now().plusDays(1);
        CycleConfig cfg = config(CycleType.BIWEEKLY, base, null);
        when(taskRepository.countBySeriesIdAndStatusAndDueDateGreaterThanEqual(eq(SERIE), eq(TaskStatus.PENDING), any()))
                .thenReturn(0L);
        when(taskRepository.findMaxDueDateBySeriesId(SERIE)).thenReturn(null); // nenhuma gerada ainda

        int criadas = materializer.materialize(cfg);

        assertEquals(CycleMaterializer.MAX_FUTURAS, criadas);
        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, times(CycleMaterializer.MAX_FUTURAS)).save(cap.capture());
        Task primeira = cap.getAllValues().get(0);
        assertEquals(base.plusWeeks(2), primeira.getDueDate(), "1ª gerada = modelo + 1 intervalo");
        assertEquals(base.plusWeeks(4), cap.getAllValues().get(1).getDueDate());
        assertEquals(LocalTime.of(9, 0), primeira.getDueTime());
        assertEquals(SERIE, primeira.getSeriesId());
        assertEquals(TaskStatus.PENDING, primeira.getStatus());
    }

    @Test
    @DisplayName("diário também gera no máximo MAX_FUTURAS (sem enxurrada)")
    void diario_nao_explode() {
        CycleConfig cfg = config(CycleType.DAILY, LocalDate.now(), null);
        when(taskRepository.countBySeriesIdAndStatusAndDueDateGreaterThanEqual(eq(SERIE), eq(TaskStatus.PENDING), any()))
                .thenReturn(0L);
        when(taskRepository.findMaxDueDateBySeriesId(SERIE)).thenReturn(null);

        int criadas = materializer.materialize(cfg);

        assertEquals(CycleMaterializer.MAX_FUTURAS, criadas);
        verify(taskRepository, times(CycleMaterializer.MAX_FUTURAS)).save(any());
    }

    @Test
    @DisplayName("já há ocorrências futuras suficientes: não gera nada")
    void ja_cheio() {
        CycleConfig cfg = config(CycleType.WEEKLY, LocalDate.now().plusDays(1), null);
        when(taskRepository.countBySeriesIdAndStatusAndDueDateGreaterThanEqual(eq(SERIE), eq(TaskStatus.PENDING), any()))
                .thenReturn((long) CycleMaterializer.MAX_FUTURAS);

        int criadas = materializer.materialize(cfg);

        assertEquals(0, criadas);
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("completa só o que falta a partir da última ocorrência existente")
    void completa_o_que_falta() {
        LocalDate ultima = LocalDate.now().plusDays(10);
        CycleConfig cfg = config(CycleType.WEEKLY, LocalDate.now(), null);
        when(taskRepository.countBySeriesIdAndStatusAndDueDateGreaterThanEqual(eq(SERIE), eq(TaskStatus.PENDING), any()))
                .thenReturn(2L); // já tem 2 → faltam 2
        when(taskRepository.findMaxDueDateBySeriesId(SERIE)).thenReturn(ultima);

        int criadas = materializer.materialize(cfg);

        assertEquals(2, criadas);
        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, times(2)).save(cap.capture());
        assertEquals(ultima.plusWeeks(1), cap.getAllValues().get(0).getDueDate(), "continua da última + 1 semana");
    }

    // ── Ciclo personalizado (CUSTOM) ────────────────────────────────────────
    private CycleConfig customConfig(IntervalUnit unit, int step, int total,
                                     LocalDate startDate, LocalTime startTime) {
        Task modelo = Task.builder()
                .id(SERIE).userId(UUID.randomUUID())
                .title("Remédio").description("dose")
                .dueDate(startDate).dueTime(startTime)
                .status(TaskStatus.PENDING)
                .build();
        return CycleConfig.builder()
                .id(UUID.randomUUID()).task(modelo)
                .cycleType(CycleType.CUSTOM)
                .intervalUnit(unit).intervalCount(step).totalOccurrences(total)
                .startDate(startDate).startTime(startTime)
                .build();
    }

    @Test
    @DisplayName("CUSTOM 12h × 7: gera a janela (MAX_FUTURAS) com data+hora corretas")
    void custom_horas() {
        LocalDate hoje = LocalDate.now();
        CycleConfig cfg = customConfig(IntervalUnit.HOURS, 12, 7, hoje, LocalTime.of(9, 0));
        when(taskRepository.countBySeriesIdAndStatusAndDueDateGreaterThanEqual(eq(SERIE), eq(TaskStatus.PENDING), any()))
                .thenReturn(0L);
        when(taskRepository.existsOccurrence(any(), any(), any())).thenReturn(false);

        int criadas = materializer.materialize(cfg);

        assertEquals(CycleMaterializer.MAX_FUTURAS, criadas);
        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, times(CycleMaterializer.MAX_FUTURAS)).save(cap.capture());
        // k=1 → hoje 09:00 +12h = hoje 21:00; k=2 → +24h = amanhã 09:00
        assertEquals(hoje, cap.getAllValues().get(0).getDueDate());
        assertEquals(LocalTime.of(21, 0), cap.getAllValues().get(0).getDueTime());
        assertEquals(hoje.plusDays(1), cap.getAllValues().get(1).getDueDate());
        assertEquals(LocalTime.of(9, 0), cap.getAllValues().get(1).getDueTime());
    }

    @Test
    @DisplayName("CUSTOM em dias: dueTime = hora do modelo; avança por dias")
    void custom_dias() {
        LocalDate hoje = LocalDate.now();
        CycleConfig cfg = customConfig(IntervalUnit.DAYS, 2, 10, hoje, LocalTime.of(8, 30));
        when(taskRepository.countBySeriesIdAndStatusAndDueDateGreaterThanEqual(eq(SERIE), eq(TaskStatus.PENDING), any()))
                .thenReturn(0L);
        when(taskRepository.existsOccurrence(any(), any(), any())).thenReturn(false);

        int criadas = materializer.materialize(cfg);

        assertEquals(CycleMaterializer.MAX_FUTURAS, criadas);
        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, times(CycleMaterializer.MAX_FUTURAS)).save(cap.capture());
        assertEquals(hoje.plusDays(2), cap.getAllValues().get(0).getDueDate());
        assertEquals(LocalTime.of(8, 30), cap.getAllValues().get(0).getDueTime());
    }

    @Test
    @DisplayName("CUSTOM: nunca passa de totalOccurrences (modelo conta como a 1ª)")
    void custom_respeita_total() {
        LocalDate hoje = LocalDate.now();
        // total = 3 → modelo + 2 geradas, mesmo que a janela (4) permitisse mais
        CycleConfig cfg = customConfig(IntervalUnit.DAYS, 1, 3, hoje, LocalTime.of(9, 0));
        when(taskRepository.countBySeriesIdAndStatusAndDueDateGreaterThanEqual(eq(SERIE), eq(TaskStatus.PENDING), any()))
                .thenReturn(0L);
        when(taskRepository.existsOccurrence(any(), any(), any())).thenReturn(false);

        int criadas = materializer.materialize(cfg);

        assertEquals(2, criadas);
        verify(taskRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("CUSTOM: idempotente — ocorrências já existentes não são duplicadas")
    void custom_idempotente() {
        LocalDate hoje = LocalDate.now();
        CycleConfig cfg = customConfig(IntervalUnit.DAYS, 1, 10, hoje, LocalTime.of(9, 0));
        when(taskRepository.countBySeriesIdAndStatusAndDueDateGreaterThanEqual(eq(SERIE), eq(TaskStatus.PENDING), any()))
                .thenReturn(0L);
        when(taskRepository.existsOccurrence(any(), any(), any())).thenReturn(true); // já materializadas

        int criadas = materializer.materialize(cfg);

        assertEquals(0, criadas);
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("respeita endDate: gera só até ele e encerra a série")
    void respeita_endDate() {
        LocalDate base = LocalDate.now().plusDays(1);
        LocalDate endDate = base.plusDays(20); // cabe 1 quinzenal (base+14); base+28 passa
        CycleConfig cfg = config(CycleType.BIWEEKLY, base, endDate);
        when(taskRepository.countBySeriesIdAndStatusAndDueDateGreaterThanEqual(eq(SERIE), eq(TaskStatus.PENDING), any()))
                .thenReturn(0L);
        when(taskRepository.findMaxDueDateBySeriesId(SERIE)).thenReturn(null);

        int criadas = materializer.materialize(cfg);

        assertEquals(1, criadas);
        assertNull(cfg.getNextResetDate(), "próxima passou do endDate → encerra");
    }
}
