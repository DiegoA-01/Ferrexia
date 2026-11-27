package com.proyecto.supply_core.grupo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Vínculo de un super administrador con su usuario Dueño dentro de una empresa del grupo (tabla
 * {@code usuario_grupo_empresa}). No lleva {@code @TenantId}: lo escribe y lo lee la consola y el login del grupo,
 * que trabajan sin empresa.
 */
@Entity
@Table(name = "usuario_grupo_empresa")
@IdClass(UsuarioGrupoEmpresaId.class)
@Getter
@NoArgsConstructor
public class UsuarioGrupoEmpresa {

    @Id
    @Column(name = "usuario_grupo_id", nullable = false, updatable = false)
    private Long usuarioGrupoId;

    @Id
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    /** Usuario Dueño (rol ADMIN_SISTEMA) dentro de la empresa. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    /**
     * @param usuarioGrupoId super administrador
     * @param empresaId      empresa del grupo
     * @param usuarioId      usuario Dueño creado en esa empresa
     */
    public UsuarioGrupoEmpresa(Long usuarioGrupoId, Long empresaId, Long usuarioId) {
        this.usuarioGrupoId = usuarioGrupoId;
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
    }
}
