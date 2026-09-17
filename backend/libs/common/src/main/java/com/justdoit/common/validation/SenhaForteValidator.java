package com.justdoit.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 *  Exige, na mesma string, ao menos uma letra
 * minúscula, uma maiúscula, um dígito e comprimento mínimo de 8 caracteres.
 *
 * Regra espelhada no frontend (mesma política, mesma ordem de checagem) para
 * que backend e frontend nunca divirjam sobre o que é "senha forte".
 */
public class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    private static final Pattern TEM_MINUSCULA = Pattern.compile("[a-z]");
    private static final Pattern TEM_MAIUSCULA = Pattern.compile("[A-Z]");
    private static final Pattern TEM_NUMERO = Pattern.compile("[0-9]");
    private static final int TAMANHO_MINIMO = 8;

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        // Nulo/vazio não é problema deste validador — ver javadoc de SenhaForte.
        if (valor == null || valor.isBlank()) {
            return true;
        }
        return valor.length() >= TAMANHO_MINIMO
                && TEM_MINUSCULA.matcher(valor).find()
                && TEM_MAIUSCULA.matcher(valor).find()
                && TEM_NUMERO.matcher(valor).find();
    }
}