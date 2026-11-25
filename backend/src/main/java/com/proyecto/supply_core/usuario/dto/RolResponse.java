package com.proyecto.supply_core.usuario.dto;

import java.util.Set;
import java.util.TreeSet;

import com.proyecto.supply_core.usuario.entity.Permiso;
import com.proyecto.supply_core.usuario.entity.Rol;

/**
 * Rol tal como lo ve la API.
 *
 * @param id          id
 * @param codigo      código estable (p. ej. {@code COMPRADOR})
 * @param nombre      nombre visible
 * @param descripcion descripción o {@code null}
 * @param plantilla   {@code true} si es una plantilla del sistema (de solo lectura); {@code false} si es propio
 * @param permisos    códigos de sus permisos (spec 022)
 */
public record RolResponse(Long id, String codigo, String nombre, String descripcion, boolean plantilla,
        Set<String> permisos) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param r entidad (con sus permisos dentro de la transacción)
     * @return DTO de respuesta
     */
    public static RolResponse of(Rol r) {
        Set<String> permisos = new TreeSet<>();
        for (Permiso p : r.getPermisos()) {
            permisos.add(p.getCodigo());
        }
        return new RolResponse(r.getId(), r.getCodigo(), r.getNombre(), r.getDescripcion(), r.getEmpresaId() == null,
                permisos);
    }
}
