package com.justdoit.task.shared;

import java.time.LocalDate;

public enum CycleType {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    ANNUAL,
    /**
     * Ciclo personalizado: o intervalo e a quantidade de repetições vêm do
     * {@code CycleConfig} (intervalUnit/intervalCount/totalOccurrences), não deste enum.
     * Por isso {@link #advance(LocalDate)} NÃO se aplica a CUSTOM — o avanço é feito
     * pelo {@code CycleMaterializer} com um cursor de data+hora.
     */
    CUSTOM;

    /**
     * Próxima ocorrência a partir de {@code from}, avançando UM intervalo do ciclo
     * (contado a partir da própria data — não faz snap para fronteira de calendário).
     * Ex.: quinzenal em 14/07 → 28/07 → 11/08. Usado para materializar as datas das
     * ocorrências futuras de uma série cíclica:
     * - DAILY   : +1 dia
     * - WEEKLY  : +1 semana (7 dias)
     * - BIWEEKLY: +2 semanas (14 dias)
     * - MONTHLY : +1 mês (mesmo dia do mês; fim de mês é limitado pelo Java)
     * - ANNUAL  : +1 ano
     */
    public LocalDate advance(LocalDate from) {
        return switch (this) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case BIWEEKLY -> from.plusWeeks(2);
            case MONTHLY -> from.plusMonths(1);
            case ANNUAL -> from.plusYears(1);
            case CUSTOM -> throw new UnsupportedOperationException(
                    "CUSTOM não avança por CycleType.advance — use CycleMaterializer com o intervalo do config");
        };
    }
}
