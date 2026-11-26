package com.proyecto.supply_core.caja.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Quiénes pueden abrir turno en una caja (spec 025). La lista reemplaza a la anterior; vacía = nadie asignado (las
 * personas sin cajas asignadas siguen pudiendo usar todas).
 *
 * @param usuarioIds personas de la empresa
 */
public record UsuariosCajaRequest(
        @NotNull(message = "es obligatoria") @Size(max = 500, message = "demasiadas personas") List<Long> usuarioIds) {
}
