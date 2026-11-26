package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;

/**
 * Un producto en el reporte de inventario.
 *
 * @param productoId      producto
 * @param codigo          código del producto
 * @param nombre          nombre del producto
 * @param categoria       nombre de la categoría
 * @param unidad          código de la unidad de medida
 * @param stock           stock total en todas las bodegas (en un producto general, la suma de sus variantes)
 * @param stockMinimo     mínimo configurado
 * @param stockMaximo     máximo configurado (puede ser {@code null})
 * @param costoReferencia último costo conocido (puede ser {@code null})
 * @param valor           stock × costo de referencia; {@code null} si no hay costo
 * @param bajoMinimo      {@code true} si el stock está por debajo del mínimo
 * @param esAgrupador     {@code true} si es un producto general que agrupa variantes (spec 028): su stock es
 *                        informativo y no suma al valor ni a las alertas
 * @param agrupadorId     producto general del que es variante, o {@code null}
 */
public record InventarioItem(Long productoId, String codigo, String nombre, String categoria, String unidad,
        BigDecimal stock, BigDecimal stockMinimo, BigDecimal stockMaximo, BigDecimal costoReferencia, BigDecimal valor,
        boolean bajoMinimo, boolean esAgrupador, Long agrupadorId) {
}
