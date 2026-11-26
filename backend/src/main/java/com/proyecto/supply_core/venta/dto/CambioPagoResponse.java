package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Un cambio del método de pago de una venta (spec 029), para el detalle.
 *
 * @param fecha         cuándo se hizo
 * @param usuario       quién lo hizo
 * @param autorizadoPor supervisor que dio su PIN, o {@code null}
 * @param turnoId       turno donde quedó el ajuste de caja
 * @param antes         monto por medio antes del cambio
 * @param despues       monto por medio después del cambio
 * @param motivo        por qué
 */
public record CambioPagoResponse(LocalDateTime fecha, String usuario, String autorizadoPor, Long turnoId,
        Map<String, BigDecimal> antes, Map<String, BigDecimal> despues, String motivo) {
}
