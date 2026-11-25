package com.proyecto.supply_core.usuario.dto;

import com.proyecto.supply_core.usuario.validation.ReglasPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El administrador asigna una contraseña nueva a otro usuario (p. ej. si la olvidó).
 *
 * @param passwordNueva clave o PIN nuevo; la regla depende de los roles (ver {@code ReglasCredencial}, D10)
 */
public record RestablecerPasswordRequest(
        @NotBlank(message = "es obligatoria")
        @Size(min = 6, max = ReglasPassword.MAXIMO, message = "debe tener entre 6 y 72 caracteres")
        String passwordNueva) {
}
