package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Datos de una combinación de valores que queda como variante (spec 028).
 *
 * @param valores         valor de cada atributo, por nombre de atributo ({@code {"Medida": "1\""}})
 * @param codigo          código único de la variante (se guarda en mayúsculas)
 * @param codigoBarras    código de barras opcional (único en la empresa)
 * @param costoReferencia costo de referencia opcional
 * @param precio          precio de venta opcional en la lista General
 * @param stockMinimo     mínimo total entre bodegas
 * @param stockMaximo     máximo total opcional (≥ mínimo)
 */
public record VarianteNuevaRequest(
        @NotEmpty(message = "es obligatorio")
        Map<String, String> valores,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,50}$", message = "de 1 a 50 caracteres: letras, números, '.', '-' o '_'")
        String codigo,

        @Size(max = 50, message = "máximo 50 caracteres")
        String codigoBarras,

        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal costoReferencia,

        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal precio,

        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal stockMinimo,

        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal stockMaximo) {

    /**
     * @return {@code true} si no hay máximo o {@code stockMaximo >= stockMinimo}
     */
    @AssertTrue(message = "stockMaximo no puede ser menor que stockMinimo")
    public boolean isRangoStockValido() {
        return stockMaximo == null || stockMinimo == null || stockMaximo.compareTo(stockMinimo) >= 0;
    }
}
