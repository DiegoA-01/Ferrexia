package com.proyecto.supply_core.usuario.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.usuario.entity.Permiso;

/** Catálogo global de permisos (spec 022, V43 y V44). */
public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    /**
     * @return todos los permisos por módulo y nombre
     */
    List<Permiso> findAllByOrderByModuloAscNombreAsc();

    /**
     * @param codigos códigos
     * @return los permisos que existen
     */
    List<Permiso> findByCodigoIn(Collection<String> codigos);
}
