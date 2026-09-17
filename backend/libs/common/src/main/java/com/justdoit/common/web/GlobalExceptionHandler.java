package com.justdoit.common.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Tratamento de erro comum aos quatro serviços (todos incluem
 * {@code com.justdoit.common} no scanBasePackages).
 *
 * A chave "error" não é decorativa: é a que o cliente lê para exibir a
 * mensagem ({@code corpo?.error} em api/client.js). Sem ela o frontend só
 * consegue mostrar "Erro 400", sem dizer ao usuário o que precisa corrigir.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        // Mantém o detalhe por campo (já consumido por quem monta formulário) e
        // acrescenta um resumo legível em "error". O resumo é calculado antes do
        // put para não incluir a si mesmo.
        String resumo = errors.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Dados inválidos");
        errors.put("error", resumo);
        return ResponseEntity.badRequest().body(errors);
    }

    /**
     * Argumento inválido que escapou do controller vira 400, não 500.
     *
     * Os controllers deste projeto capturam {@code IllegalArgumentException}
     * caso a caso (em geral traduzindo para 404). Onde a captura falta, a
     * exceção subia como 500 — foi o que acontecia no {@code POST /tasks} com
     * um categoryId de outro usuário: a tarefa não era criada e o cliente
     * recebia erro de servidor, sem pista do motivo.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        String mensagem = ex.getMessage() != null ? ex.getMessage() : "Requisição inválida";
        return ResponseEntity.badRequest().body(Map.of("error", mensagem));
    }
}
