package com.justdoit.auth.feature.auth;

/**
 * Verifica se um e-mail é "entregável" — isto é, se o domínio aceita e-mails.
 *
 * Implementação atual ({@link MxEmailVerifier}) usa consulta DNS de registros MX.
 * A interface existe para permitir trocar/plugar uma API externa de verificação
 * no futuro sem alterar o controller ou o serviço.
 */
public interface EmailVerifier {

    /** @return true se o domínio do e-mail aceita correio (best-effort). */
    boolean isDeliverable(String email);
}
