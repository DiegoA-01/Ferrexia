package com.proyecto.supply_core.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restringe un endpoint (o todo un controller) a quien tenga el permiso indicado (spec 022). El permiso puede venir de
 * cualquiera de sus roles o sumarse a mano a la persona; también se le puede quitar a mano.
 * <p>Uso: {@code @RequierePermiso(Permisos.VENTA_COBRAR)}. La anotación del método reemplaza a la de la clase. Sin
 * anotación, el endpoint solo exige estar autenticado.</p>
 */
@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequierePermiso {

    /**
     * Permiso exigido (constante de {@code Permisos}).
     *
     * @return código del permiso
     */
    String value();
}
