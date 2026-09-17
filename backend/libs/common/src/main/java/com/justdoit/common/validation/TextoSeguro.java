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
 * Marca um campo de texto livre que o usuário preenche e que, portanto, não é
 * confiável. Entrada com indício de código malicioso (HTML/script, handler de
 * evento, URI javascript:, injeção de expressão ou SQL) é REJEITADA — a
 * requisição volta 400 pelo {@code GlobalExceptionHandler}, e nada é persistido.
 *
 * Política de Zero Trust: rejeitar em vez de limpar. Sanitizar silenciosamente
 * alteraria o conteúdo sem o usuário perceber; o 400 é explícito.
 *
 * NÃO deve ser usada em campo de senha, token ou e-mail: senha legitimamente
 * contém {@code < ' ;} e barrá-los enfraqueceria a política de senhas.
 *
 * @see TextoSeguroValidator para os padrões detectados
 */
@Documented
@Constraint(validatedBy = TextoSeguroValidator.class)
@Target({FIELD, METHOD, PARAMETER, CONSTRUCTOR, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface TextoSeguro {

    String message() default "Conteúdo não permitido: possível código malicioso";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
