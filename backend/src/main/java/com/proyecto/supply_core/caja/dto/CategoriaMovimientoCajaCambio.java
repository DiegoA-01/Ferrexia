package com.proyecto.supply_core.caja.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Renombrar, desactivar o reactivar una categoría de caja (el tipo no cambia).
 *
 * @param nombre nombre nuevo
 * @param activa si se puede elegir al registrar un movimiento
 */
public record CategoriaMovimientoCajaCambio(
        @NotBlank(message = "es obligatorio") @Size(max = 80, message = "máximo 80 caracteres") String nombre,
        @NotNull(message = "es obligatorio") Boolean activa) {
}
