package com.proyecto.supply_core.grupo.entity;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Clave compuesta de {@link UsuarioGrupoEmpresa}: super administrador y empresa. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UsuarioGrupoEmpresaId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long usuarioGrupoId;
    private Long empresaId;
}
