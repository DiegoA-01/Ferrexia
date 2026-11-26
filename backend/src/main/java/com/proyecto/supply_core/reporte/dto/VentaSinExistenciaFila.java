package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una venta sin existencia (spec 030): lo que faltaba al vender y lo que sigue pendiente de descontar.
 *
 * @param id             faltante
 * @param fecha          cuándo se vendió
 * @param ventaId        venta
 * @param consecutivo    consecutivo de la venta
 * @param caja           caja de la venta
 * @param cajero         quien vendió sin existencia
 * @param productoId     producto
 * @param codigo         código del producto
 * @param producto       nombre del producto
 * @param unidad         unidad del producto
 * @param cantidad       lo que faltaba al vender
 * @param pendiente      lo que falta por descontar
 * @param motivo         motivo que escribió el cajero
 * @param estado         PENDIENTE, REGULARIZADA o ANULADA
 * @param regularizadaEn cuándo se terminó de descontar, o {@code null}
 */
public record VentaSinExistenciaFila(Long id, LocalDateTime fecha, Long ventaId, String consecutivo, String caja,
        String cajero, Long productoId, String codigo, String producto, String unidad, BigDecimal cantidad,
        BigDecimal pendiente, String motivo, String estado, LocalDateTime regularizadaEn) {
}
