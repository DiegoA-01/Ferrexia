package com.proyecto.supply_core.compra.solicitud.enums;

/** A quién se le pide la solicitud de compra (spec 033). */
public enum ModoProveedor {
    /** Todos los proveedores habilitados de las categorías de sus productos (como antes de la spec 033). */
    ABIERTA,
    /** Solo los proveedores elegidos al crear la solicitud. */
    ESPECIFICA
}
