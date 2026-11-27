package com.proyecto.supply_core.grupo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.grupo.entity.UsuarioGrupoEmpresa;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupoEmpresaId;

/** Acceso a la tabla {@code usuario_grupo_empresa}. */
public interface UsuarioGrupoEmpresaRepository extends JpaRepository<UsuarioGrupoEmpresa, UsuarioGrupoEmpresaId> {

    /**
     * Vínculo de un super administrador con una empresa.
     *
     * @param usuarioGrupoId super administrador
     * @param empresaId      empresa
     * @return vínculo, si existe
     */
    Optional<UsuarioGrupoEmpresa> findByUsuarioGrupoIdAndEmpresaId(Long usuarioGrupoId, Long empresaId);

    /**
     * Vínculos de un super administrador con sus empresas.
     *
     * @param usuarioGrupoId super administrador
     * @return vínculos
     */
    List<UsuarioGrupoEmpresa> findByUsuarioGrupoId(Long usuarioGrupoId);
}
