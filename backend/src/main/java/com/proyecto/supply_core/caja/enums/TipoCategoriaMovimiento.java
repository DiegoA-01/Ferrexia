package com.proyecto.supply_core.caja.enums;

/** Si una categoría de caja es de dinero que entra (ingreso) o que sale (retiro o gasto) (spec 021). */
public enum TipoCategoriaMovimiento {
    /** Dinero que entra a la caja. */
    INGRESO,
    /** Dinero que sale de la caja: gastos y, si se quiere, retiros. */
    EGRESO
}
