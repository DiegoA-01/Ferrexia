package com.proyecto.supply_core.grupo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.grupo.entity.GrupoEmpresa;

/** Acceso a la tabla {@code grupo_empresa}. */
public interface GrupoEmpresaRepository extends JpaRepository<GrupoEmpresa, Long> {

    /**
     * Indica si ya existe un grupo con ese nombre.
     *
     * @param nombre nombre del grupo
     * @return {@code true} si existe
     */
    boolean existsByNombreIgnoreCase(String nombre);
}
