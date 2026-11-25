package com.proyecto.supply_core.empresa.dto;

import com.proyecto.supply_core.empresa.entity.Sede;

/**
 * Sede tal como la ve la API.
 *
 * @param id         id
 * @param nombre     nombre
 * @param direccion  dirección
 * @param enlaceMapa enlace de Google Maps o {@code null} (spec 020)
 * @param activo     si se puede usar en nuevas operaciones
 */
public record SedeResponse(Long id, String nombre, String direccion, String enlaceMapa, boolean activo) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param s sede
     * @return DTO
     */
    public static SedeResponse of(Sede s) {
        return new SedeResponse(s.getId(), s.getNombre(), s.getDireccion(), s.getEnlaceMapa(), s.isActivo());
    }
}
