package com.proyecto.supply_core.caja.dto;

import com.proyecto.supply_core.caja.entity.CategoriaMovimientoCaja;
import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;

/**
 * Categoría de ingreso o egreso de caja.
 *
 * @param id     categoría
 * @param tipo   INGRESO o EGRESO
 * @param nombre nombre
 * @param activa si se puede elegir
 */
public record CategoriaMovimientoCajaResponse(Long id, TipoCategoriaMovimiento tipo, String nombre, boolean activa) {

    /**
     * @param c categoría
     * @return respuesta
     */
    public static CategoriaMovimientoCajaResponse of(CategoriaMovimientoCaja c) {
        return new CategoriaMovimientoCajaResponse(c.getId(), c.getTipo(), c.getNombre(), c.isActiva());
    }
}
