package com.justdoit.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marca um campo de senha que deve atender à política mínima de segurança:
 * ao menos 8 caracteres, com letra maiúscula, minúscula e número.
 *
 * A mensagem de erro é sempre a política completa, nunca aponta qual regra
 * específica falhou — evita dar pista útil para um atacante testando senhas
 * e mantém a orientação clara para o usuário legítimo.
 *
 * Nulo/vazio é considerado VÁLIDO por este validador: quem exige presença é
 * @NotBlank (cadastro) ou a regra de negócio (troca de senha, campo opcional).
 *
 * @see SenhaForteValidator para as regras verificadas
 */
@Documented
@Constraint(validatedBy = SenhaForteValidator.class)
@Target({FIELD, METHOD, PARAMETER, CONSTRUCTOR, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface SenhaForte {

    String message() default "A senha deve ter no mínimo 8 caracteres, "
            + "incluindo ao menos uma letra maiúscula, uma minúscula e um número";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}