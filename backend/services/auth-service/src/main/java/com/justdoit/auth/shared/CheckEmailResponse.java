package com.justdoit.auth.shared;

/**
 * Resposta da verificação prévia de e-mail (GET /auth/check-email).
 *
 * @param email        e-mail consultado (normalizado)
 * @param registered   já existe um usuário com este e-mail
 * @param deliverable  o domínio aceita e-mail (possui registro MX/A)
 * @param available    pode ser usado no cadastro (não registrado e entregável)
 */
public record CheckEmailResponse(String email, boolean registered, boolean deliverable, boolean available) {}
