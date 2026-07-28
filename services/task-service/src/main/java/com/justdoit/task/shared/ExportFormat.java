package com.justdoit.task.shared;

import java.util.Locale;

/**
 * Formatos aceitos pela exportação de dados do usuário (/me/export).
 * O parse é tolerante a caixa porque o front manda "csv"/"json" em minúsculas,
 * enquanto o enum segue a convenção do projeto (MAIÚSCULAS, como TaskStatus).
 */
public enum ExportFormat {
    CSV,
    JSON;

    /** Ausente/vazio cai em JSON; qualquer outro valor é erro do cliente (400). */
    public static ExportFormat from(String value) {
        if (value == null || value.isBlank()) return JSON;
        try {
            return ExportFormat.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Formato inválido: use csv ou json");
        }
    }
}
