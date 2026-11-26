package com.proyecto.supply_core.caja.dto;

/**
 * Caja asignada a una persona, para su ficha (spec 025).
 *
 * @param cajaId caja
 * @param nombre nombre de la caja
 */
public record CajaAsignadaResponse(Long cajaId, String nombre) {
}
