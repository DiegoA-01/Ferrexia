package com.proyecto.supply_core.catalogo.enums;

/**
 * Qué mide una unidad (ENUM {@code unidad_medida.magnitud}, migración V5).
 * Solo se convierte entre unidades de la misma magnitud.
 */
public enum Magnitud {
    /** Piezas contables (base: UND). */
    CANTIDAD,
    /** Peso (base: KG). */
    MASA,
    /** Capacidad (base: LT). */
    VOLUMEN,
    /** Distancia (base: M). */
    LONGITUD,
    /** Superficie (base: M2), spec 038. */
    AREA
}
