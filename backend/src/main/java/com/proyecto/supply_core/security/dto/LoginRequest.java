package com.proyecto.supply_core.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de inicio de sesión (UC-01). El correo es único en todo el sistema (spec 037): con él se encuentra la
 * empresa, así que ya no se manda un código de empresa.
 *
 * @param correo   correo registrado
 * @param password contraseña (o PIN) en texto plano (solo viaja por HTTPS; nunca se guarda)
 */
public record LoginRequest(
        @NotBlank(message = "es obligatorio")
        @Email(message = "no tiene un formato de correo válido")
        @Size(max = 150, message = "máximo 150 caracteres")
        String correo,

        @NotBlank(message = "es obligatoria")
        @Size(max = 72, message = "máximo 72 caracteres")
        String password) {
}
