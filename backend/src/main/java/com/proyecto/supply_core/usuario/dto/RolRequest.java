package com.proyecto.supply_core.usuario.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Rol propio de la empresa (spec 022). Para "copiar una plantilla", la pantalla manda los permisos de la plantilla ya
 * cambiados.
 *
 * @param nombre      nombre visible, único entre los roles que ve la empresa
 * @param descripcion para qué sirve, opcional
 * @param permisos    códigos de permiso
 */
public record RolRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 100, message = "máximo 100 caracteres") String nombre,
        @Size(max = 250, message = "máximo 250 caracteres") String descripcion,
        @NotEmpty(message = "elija al menos un permiso") Set<@NotBlank(message = "no puede estar vacío") String> permisos) {
}
