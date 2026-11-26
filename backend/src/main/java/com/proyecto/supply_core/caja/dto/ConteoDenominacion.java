package com.proyecto.supply_core.caja.dto;

import com.proyecto.supply_core.caja.enums.TipoDenominacion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuántos billetes o monedas de un valor contó el cajero al cerrar (spec 023).
 *
 * @param tipo       billete o moneda
 * @param valor      valor en pesos de la denominación (por ejemplo 50000)
 * @param cantidad   unidades contadas
 * @param referencia nota corta de la fila, opcional
 */
public record ConteoDenominacion(
        @NotNull(message = "es obligatorio") TipoDenominacion tipo,
        @NotNull(message = "es obligatorio") Integer valor,
        @NotNull(message = "es obligatoria")
        @Min(value = 0, message = "no puede ser negativa")
        @Max(value = 100_000, message = "máximo 100.000 unidades")
        Integer cantidad,
        @Size(max = 100, message = "máximo 100 caracteres") String referencia) {
}
