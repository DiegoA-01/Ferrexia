package com.proyecto.supply_core.caja.dto;

/**
 * Persona asignada a una caja (spec 025).
 *
 * @param usuarioId persona
 * @param nombre    nombre completo
 */
public record UsuarioCajaResponse(Long usuarioId, String nombre) {
}
