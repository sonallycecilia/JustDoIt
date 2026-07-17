package com.justdoit.task.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CycleTypeTest {

    private static final LocalDate BASE = LocalDate.of(2026, 7, 14); // terça

    @Test
    @DisplayName("DAILY: +1 dia")
    void daily() {
        assertEquals(LocalDate.of(2026, 7, 15), CycleType.DAILY.advance(BASE));
    }

    @Test
    @DisplayName("WEEKLY: +7 dias (mesmo dia da semana, sem snap)")
    void weekly() {
        assertEquals(LocalDate.of(2026, 7, 21), CycleType.WEEKLY.advance(BASE));
    }

    @Test
    @DisplayName("BIWEEKLY: +14 dias (ex.: 14/07 → 28/07 → 11/08)")
    void biweekly() {
        LocalDate d1 = CycleType.BIWEEKLY.advance(BASE);
        LocalDate d2 = CycleType.BIWEEKLY.advance(d1);
        assertEquals(LocalDate.of(2026, 7, 28), d1);
        assertEquals(LocalDate.of(2026, 8, 11), d2);
    }

    @Test
    @DisplayName("MONTHLY: +1 mês (mesmo dia)")
    void monthly() {
        assertEquals(LocalDate.of(2026, 8, 14), CycleType.MONTHLY.advance(BASE));
        // fim de mês é limitado pelo Java (31/01 + 1 mês = 28/02)
        assertEquals(LocalDate.of(2026, 2, 28), CycleType.MONTHLY.advance(LocalDate.of(2026, 1, 31)));
    }

    @Test
    @DisplayName("ANNUAL: +1 ano (mesmo dia)")
    void annual() {
        assertEquals(LocalDate.of(2027, 7, 14), CycleType.ANNUAL.advance(BASE));
    }

    @Test
    @DisplayName("CUSTOM: advance não se aplica (avanço é do CycleMaterializer)")
    void custom() {
        assertThrows(UnsupportedOperationException.class, () -> CycleType.CUSTOM.advance(BASE));
    }
}
