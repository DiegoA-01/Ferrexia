package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;

/**
 * Fila agregada que devuelve la base de datos: un producto con su stock sumado en todas las bodegas.
 *
 * @param productoId      producto
 * @param codigo          código del producto
 * @param nombre          nombre del producto
 * @param categoria       nombre de la categoría
 * @param unidad          código de la unidad de medida
 * @param stock           stock total (0 si nunca tuvo movimientos)
 * @param stockMinimo     mínimo configurado
 * @param stockMaximo     máximo configurado (puede ser {@code null})
 * @param costoReferencia último costo conocido (puede ser {@code null})
 * @param esAgrupador     {@code true} si es un producto general que agrupa variantes (spec 028)
 * @param agrupadorId     producto general del que es variante, o {@code null}
 */
public record InventarioFila(Long productoId, String codigo, String nombre, String categoria, String unidad,
        BigDecimal stock, BigDecimal stockMinimo, BigDecimal stockMaximo, BigDecimal costoReferencia,
        boolean esAgrupador, Long agrupadorId) {

    /**
     * Fila de un producto sin variantes (como antes de la spec 028).
     *
     * @param productoId      producto
     * @param codigo          código
     * @param nombre          nombre
     * @param categoria       categoría
     * @param unidad          unidad
     * @param stock           stock total
     * @param stockMinimo     mínimo
     * @param stockMaximo     máximo
     * @param costoReferencia costo de referencia
     */
    public InventarioFila(Long productoId, String codigo, String nombre, String categoria, String unidad,
            BigDecimal stock, BigDecimal stockMinimo, BigDecimal stockMaximo, BigDecimal costoReferencia) {
        this(productoId, codigo, nombre, categoria, unidad, stock, stockMinimo, stockMaximo, costoReferencia, false,
                null);
    }
}
