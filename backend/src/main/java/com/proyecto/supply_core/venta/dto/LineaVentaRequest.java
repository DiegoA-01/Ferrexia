package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Línea que manda la pantalla: solo qué, cuánto y qué descuento pide. El precio y los montos los pone Java.
 *
 * @param productoId     producto
 * @param unidadMedidaId unidad en que se vende ({@code null} = la del producto)
 * @param cantidad       cantidad (> 0, hasta 4 decimales)
 * @param descuentoPct   descuento pedido en porcentaje ({@code null} = 0)
 * @param sinExistencia  {@code true} para venderla aunque no haya existencia suficiente (spec 030; exige el permiso)
 * @param motivoSinExistencia por qué se vende sin existencia (obligatorio si {@code sinExistencia})
 */
public record LineaVentaRequest(
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser un id válido") Long productoId,
        @Positive(message = "debe ser un id válido") Long unidadMedidaId,
        @NotNull(message = "es obligatoria") @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "admite hasta 4 decimales") BigDecimal cantidad,
        @DecimalMin(value = "0", message = "no puede ser negativo") @DecimalMax(value = "100", message = "no puede pasar de 100")
        @Digits(integer = 3, fraction = 2, message = "admite hasta 2 decimales") BigDecimal descuentoPct,
        Boolean sinExistencia,
        @jakarta.validation.constraints.Size(max = 200, message = "admite hasta 200 caracteres") String motivoSinExistencia) {

    /**
     * Línea normal (sin venta sin existencia), como antes de la spec 030.
     *
     * @param productoId     producto
     * @param unidadMedidaId unidad
     * @param cantidad       cantidad
     * @param descuentoPct   descuento
     */
    public LineaVentaRequest(Long productoId, Long unidadMedidaId, BigDecimal cantidad, BigDecimal descuentoPct) {
        this(productoId, unidadMedidaId, cantidad, descuentoPct, null, null);
    }

    /**
     * @return {@code true} si se pidió vender sin existencia
     */
    public boolean esSinExistencia() {
        return Boolean.TRUE.equals(sinExistencia);
    }

    /**
     * Regla: vender sin existencia exige un motivo.
     *
     * @return {@code true} si no se pidió o trae motivo
     */
    @jakarta.validation.constraints.AssertTrue(message = "para vender sin existencia escriba el motivo")
    public boolean isMotivoSinExistenciaValido() {
        return !esSinExistencia() || (motivoSinExistencia != null && motivoSinExistencia.trim().length() >= 3);
    }
}
