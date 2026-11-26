package com.proyecto.supply_core.venta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Anulación de una venta de un turno ya cerrado (spec 029): la hace quien tiene el permiso, sin PIN.
 *
 * @param motivo por qué se anula
 */
public record AnularOtroTurnoRequest(
        @NotBlank(message = "es obligatorio") @Size(min = 5, max = 300, message = "debe tener entre 5 y 300 caracteres")
        String motivo) {
}
