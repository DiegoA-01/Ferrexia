package com.proyecto.supply_core.empresa.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * El texto debe ser un enlace {@code https} de Google Maps (spec 020): {@code google.com/maps…},
 * {@code maps.google.com…}, {@code maps.app.goo.gl/…} o {@code goo.gl/maps/…}. {@code null} o vacío se aceptan (el
 * enlace es opcional).
 */
@Documented
@Constraint(validatedBy = EnlaceGoogleMapsValidator.class)
@Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface EnlaceGoogleMaps {

    /**
     * Mensaje de error.
     *
     * @return texto que acompaña al nombre del campo
     */
    String message() default "debe ser un enlace de Google Maps (https)";

    /**
     * Grupos de validación (estándar de Bean Validation).
     *
     * @return grupos
     */
    Class<?>[] groups() default {};

    /**
     * Metadatos adicionales (estándar de Bean Validation).
     *
     * @return payload
     */
    Class<? extends Payload>[] payload() default {};
}
