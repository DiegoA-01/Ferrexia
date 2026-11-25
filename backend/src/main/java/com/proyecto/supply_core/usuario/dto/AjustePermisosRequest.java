package com.proyecto.supply_core.usuario.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Permisos sueltos de una persona (spec 022). Reemplaza todos los ajustes anteriores: lo que no viene vuelve a ser como
 * dice su rol.
 *
 * @param ajustes permisos sumados o quitados
 */
public record AjustePermisosRequest(
        @NotNull(message = "es obligatorio") @Size(max = 200, message = "demasiados ajustes") List<@Valid Ajuste> ajustes) {

    /**
     * @param permiso   código del permiso
     * @param concedido {@code true} suma un permiso que el rol no da; {@code false} quita uno que sí da
     */
    public record Ajuste(@NotBlank(message = "es obligatorio") String permiso,
            @NotNull(message = "es obligatorio") Boolean concedido) {
    }
}
