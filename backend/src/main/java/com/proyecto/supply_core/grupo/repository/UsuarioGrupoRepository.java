package com.proyecto.supply_core.grupo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.grupo.entity.UsuarioGrupo;

/** Acceso a la tabla {@code usuario_grupo}. */
public interface UsuarioGrupoRepository extends JpaRepository<UsuarioGrupo, Long> {

    /**
     * Busca por correo sin distinguir mayúsculas.
     *
     * @param correo correo de login
     * @return super administrador, si existe
     */
    Optional<UsuarioGrupo> findByCorreoIgnoreCase(String correo);

    /**
     * Indica si el correo ya lo usa un super administrador.
     *
     * @param correo correo a verificar
     * @return {@code true} si existe
     */
    boolean existsByCorreoIgnoreCase(String correo);

    /**
     * Super administradores de un grupo, el más antiguo primero.
     *
     * @param grupoId grupo
     * @return super administradores (activos o no)
     */
    List<UsuarioGrupo> findByGrupoIdOrderByIdAsc(Long grupoId);
}
