package com.proyecto.supply_core.security;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.proyecto.supply_core.security.service.PermisosService;

/**
 * Se registra sola en todas las pruebas ({@code junit-platform.properties}): las reglas de los servicios que preguntan
 * por permisos (spec 022) deciden con lo que siembran las migraciones cuando la prueba no levanta Spring.
 */
public class PermisosDePruebaExtension implements BeforeAllCallback {

    /** @param context contexto de la clase de prueba */
    @Override
    public void beforeAll(ExtensionContext context) {
        PermisosService.usarRespaldo(PermisosDeMigraciones::tiene);
    }
}
