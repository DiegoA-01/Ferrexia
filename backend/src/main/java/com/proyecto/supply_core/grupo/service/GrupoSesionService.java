package com.proyecto.supply_core.grupo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupo;
import com.proyecto.supply_core.grupo.repository.GrupoEmpresaRepository;
import com.proyecto.supply_core.grupo.repository.UsuarioGrupoRepository;
import com.proyecto.supply_core.security.context.GrupoAutenticado;

/**
 * Decide si el JWT de un super administrador de grupo sigue valiendo. Consulta la base en cada petición (la zona del
 * grupo tiene poco tráfico), así que desactivar al super administrador o al grupo corta su token de inmediato.
 */
@Service
public class GrupoSesionService {

    /** Mensaje único: no se explica si fue por desactivación del usuario o del grupo. */
    static final String SESION_CERRADA = "La sesión ya no es válida, inicie sesión de nuevo";

    private final UsuarioGrupoRepository usuarioRepository;
    private final GrupoEmpresaRepository grupoRepository;

    /**
     * @param usuarioRepository super administradores
     * @param grupoRepository   grupos
     */
    public GrupoSesionService(UsuarioGrupoRepository usuarioRepository, GrupoEmpresaRepository grupoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.grupoRepository = grupoRepository;
    }

    /**
     * Exige que el token siga vigente: el super administrador existe, está activo, sigue en el mismo grupo (también
     * activo) y tiene la misma versión de sesión con la que se emitió el token. Debe llamarse sin empresa en el contexto.
     *
     * @param usuario       datos del token ya validado
     * @param versionToken  versión de sesión con la que se emitió
     * @throws NoAutenticadoException si la cuenta o el grupo se desactivaron después de emitir el token
     */
    @Transactional(readOnly = true)
    public void exigirVigente(GrupoAutenticado usuario, int versionToken) {
        UsuarioGrupo u = usuarioRepository.findById(usuario.id()).orElse(null);
        if (u == null || !u.isActivo() || !u.getGrupoId().equals(usuario.grupoId())
                || u.getVersionSesion() != versionToken
                || !grupoRepository.findById(u.getGrupoId()).map(g -> g.isActivo()).orElse(false)) {
            throw new NoAutenticadoException(SESION_CERRADA);
        }
    }
}
