package com.proyecto.supply_core.usuario.dto;

import com.proyecto.supply_core.usuario.entity.Permiso;

/**
 * Un permiso del catálogo (spec 022).
 *
 * @param codigo código estable
 * @param nombre qué permite
 * @param modulo módulo en el que se agrupa
 */
public record PermisoResponse(String codigo, String nombre, String modulo) {

    /**
     * @param p entidad
     * @return respuesta
     */
    public static PermisoResponse of(Permiso p) {
        return new PermisoResponse(p.getCodigo(), p.getNombre(), p.getModulo());
    }
}
