package com.proyecto.supply_core.inventario.enums;

/**
 * Tipo de movimiento de inventario (ENUM {@code movimiento_inventario.tipo}).
 * <p>Cada tipo define qué bodega usa: las entradas usan destino, las salidas usan origen y el
 * traslado usa ambas. Solo los tipos {@link #esManual() manuales} se registran por la API;
 * ENTRADA_COMPRA y DEVOLUCION_PROVEEDOR los genera la recepción de mercancía (paso 17), porque
 * la recepción es el único evento que aumenta el stock por una compra. SALIDA_VENTA y
 * ANULACION_DESPACHO los genera el despacho al confirmarse o anularse.</p>
 */
public enum TipoMovimiento {
    /** Entrada por recepción de una orden de compra (solo desde recepción). */
    ENTRADA_COMPRA,
    /** Salida por consumo interno. */
    SALIDA_CONSUMO,
    /** Ajuste que suma (p. ej. conteo físico mayor, inventario inicial). Exige motivo. */
    AJUSTE_POSITIVO,
    /** Ajuste que resta (p. ej. pérdida, daño). Exige motivo. */
    AJUSTE_NEGATIVO,
    /** Salida por devolución al proveedor (solo desde recepción). Exige motivo. */
    DEVOLUCION_PROVEEDOR,
    /** Paso de stock de una bodega a otra. */
    TRASLADO,
    /** Salida por despacho (venta o entrega) confirmado (solo desde {@code DespachoService}). */
    SALIDA_VENTA,
    /** Entrada que devuelve al stock lo de un despacho anulado (solo desde {@code DespachoService}). Exige motivo. */
    ANULACION_DESPACHO,
    /** Entrada que devuelve al stock lo de una venta anulada en su turno (spec 006). */
    ANULACION_VENTA,
    /** Entrada por la mercancía que devuelve un cliente (spec 006). */
    DEVOLUCION_CLIENTE,
    /** Salida de la empresa origen al enviar un traslado a otra empresa del grupo (spec 035). */
    SALIDA_TRASLADO_GRUPO,
    /** Entrada en la empresa destino al recibir un traslado de otra empresa del grupo (spec 035). */
    ENTRADA_TRASLADO_GRUPO;

    /**
     * Indica si el almacenista lo puede registrar directamente (UC-03).
     *
     * @return {@code true} para salida, ajustes y traslado
     */
    public boolean esManual() {
        return this == SALIDA_CONSUMO || this == AJUSTE_POSITIVO || this == AJUSTE_NEGATIVO || this == TRASLADO;
    }

    /**
     * Indica si resta stock de una bodega de origen.
     *
     * @return {@code true} para salidas (consumo, venta y traslado a otra empresa), ajuste negativo, devolución y traslado
     */
    public boolean usaOrigen() {
        return this == SALIDA_CONSUMO || this == AJUSTE_NEGATIVO || this == DEVOLUCION_PROVEEDOR || this == TRASLADO
                || this == SALIDA_VENTA || this == SALIDA_TRASLADO_GRUPO;
    }

    /**
     * Indica si suma stock a una bodega de destino.
     *
     * @return {@code true} para entrada de compra, ajuste positivo, traslado y anulación de despacho
     */
    public boolean usaDestino() {
        return this == ENTRADA_COMPRA || this == AJUSTE_POSITIVO || this == TRASLADO || this == ANULACION_DESPACHO
                || this == ANULACION_VENTA || this == DEVOLUCION_CLIENTE
                || this == ENTRADA_TRASLADO_GRUPO;
    }

    /**
     * Indica si el motivo es obligatorio.
     *
     * @return {@code true} para ajustes, devolución y anulación de despacho
     */
    public boolean exigeMotivo() {
        return this == AJUSTE_POSITIVO || this == AJUSTE_NEGATIVO || this == DEVOLUCION_PROVEEDOR
                || this == ANULACION_DESPACHO || this == ANULACION_VENTA || this == DEVOLUCION_CLIENTE;
    }
}
