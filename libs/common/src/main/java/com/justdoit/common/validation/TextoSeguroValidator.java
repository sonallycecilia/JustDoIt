package com.justdoit.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Implementa {@link TextoSeguro}. Rejeita o texto se ele casar com qualquer um
 * dos padrões abaixo.
 *
 * A lista é deliberadamente ESPECÍFICA, não genérica: o objetivo é zero falso
 * positivo em texto legítimo em português. Caracteres que aparecem em uso normal
 * — {@code --} isolado, apóstrofo isolado ({@code D'Ávila}), {@code <} seguido de
 * número ({@code orçamento < 500}), {@code & % +} — NÃO são bloqueados.
 *
 * SQL Injection já é estruturalmente impossível no projeto (todo acesso é via
 * Spring Data parametrizado, sem query nativa concatenada). Os padrões de SQL
 * aqui são defesa em profundidade, não a proteção principal.
 */
public class TextoSeguroValidator implements ConstraintValidator<TextoSeguro, String> {

    private static final List<Pattern> PADROES_PERIGOSOS = List.of(
            // Abertura de tag HTML: "<" com o nome da tag COLADO (com "/" opcional).
            // O espaço não é tolerado de propósito: navegador não interpreta
            // "< script>" como tag, então bloquear isso só geraria falso positivo
            // em texto legítimo ("a < b", "orçamento < 500").
            Pattern.compile("</?[a-zA-Z]"),

            // Handlers de evento inline. Lista fechada para não pegar palavras
            // comuns terminadas em "on" seguidas de "=" (ex.: "onde =").
            Pattern.compile("(?i)\\b(onerror|onload|onclick|onmouseover|onmouseout"
                    + "|onfocus|onblur|onsubmit|onchange|onkeydown|onkeypress)\\s*="),

            // URIs que executam código.
            Pattern.compile("(?i)javascript\\s*:"),
            Pattern.compile("(?i)vbscript\\s*:"),
            Pattern.compile("(?i)data\\s*:\\s*text/html"),

            // Injeção de expressão (SpEL, EL, JNDI/Log4Shell).
            Pattern.compile("\\$\\{"),
            Pattern.compile("#\\{"),

            // SQL — comandos completos.
            Pattern.compile("(?i)\\b(union\\s+select|drop\\s+table|delete\\s+from"
                    + "|insert\\s+into|update\\s+\\w+\\s+set)\\b"),

            // SQL — tautologia clássica: 1' OR '1'='1
            Pattern.compile("(?i)'\\s*(or|and)\\s*'?\\d*'?\\s*="),

            // SQL — comentário após aspa (admin'--) e comando encadeado ("; DROP).
            Pattern.compile("'\\s*--"),
            Pattern.compile("(?i);\\s*(drop|delete|update|insert|truncate)\\b"),

            // Byte nulo usado para truncar strings em camadas nativas.
            Pattern.compile("\\x00")
    );

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        // Nulo/vazio não é problema deste validador: quem exige preenchimento é
        // o @NotBlank. Manter as responsabilidades separadas evita mensagem de
        // erro enganosa ("código malicioso") num campo simplesmente vazio.
        if (valor == null || valor.isBlank()) {
            return true;
        }
        return PADROES_PERIGOSOS.stream().noneMatch(padrao -> padrao.matcher(valor).find());
    }
}
