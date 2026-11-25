package com.proyecto.supply_core.caja.dto;

import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Categoría nueva de ingreso o egreso de caja.
 *
 * @param tipo   INGRESO o EGRESO
 * @param nombre nombre visible (por ejemplo "Papelería")
 */
public record CategoriaMovimientoCajaRequest(
        @NotNull(message = "es obligatorio") TipoCategoriaMovimiento tipo,
        @NotBlank(message = "es obligatorio") @Size(max = 80, message = "máximo 80 caracteres") String nombre) {
}
