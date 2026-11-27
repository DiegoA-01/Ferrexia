package com.proyecto.supply_core.grupo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.EmpresaEntrableResponse;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupoEmpresa;
import com.proyecto.supply_core.grupo.repository.UsuarioGrupoEmpresaRepository;
import com.proyecto.supply_core.plataforma.entity.Suscripcion;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;
import com.proyecto.supply_core.security.context.GrupoActual;
import com.proyecto.supply_core.security.context.GrupoAutenticado;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.service.AuthService;

/**
 * Lo que el super administrador de un grupo hace con las empresas del grupo: verlas y entrar a una sin volver a
 * escribir su clave. Trabaja sin empresa en el contexto y solo entra a una con {@code EmpresaContexto.como}.
 */
@Service
public class GrupoEmpresaService {

    /** Mismo mensaje para una empresa que no existe, que es de otro grupo o a la que no hay vínculo activo. */
    static final String EMPRESA_NO_EXISTE = "La empresa indicada no existe";

    private final EmpresaRepository empresaRepository;
    private final UsuarioGrupoEmpresaRepository vinculoRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final AuthService authService;

    /**
     * @param empresaRepository     empresas
     * @param vinculoRepository     vínculos del super administrador con cada empresa
     * @param suscripcionRepository estado de la suscripción de cada empresa
     * @param authService           emisión de la sesión normal de la empresa
     */
    public GrupoEmpresaService(EmpresaRepository empresaRepository, UsuarioGrupoEmpresaRepository vinculoRepository,
            SuscripcionRepository suscripcionRepository, AuthService authService) {
        this.empresaRepository = empresaRepository;
        this.vinculoRepository = vinculoRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.authService = authService;
    }

    /**
     * Empresas del grupo del token a las que puede entrar: las que tienen su usuario vinculado y una suscripción que
     * permite el acceso.
     *
     * @return empresas del grupo, la más antigua primero
     */
    @Transactional(readOnly = true)
    public List<EmpresaEntrableResponse> empresas() {
        GrupoAutenticado u = GrupoActual.usuario();
        List<EmpresaEntrableResponse> resultado = new ArrayList<>();
        for (Empresa e : empresaRepository.findByGrupoIdOrderByIdAsc(u.grupoId())) {
            if (vinculoRepository.findByUsuarioGrupoIdAndEmpresaId(u.id(), e.getId()).isEmpty()) {
                continue;
            }
            Optional<Suscripcion> s = suscripcionRepository.findByEmpresaId(e.getId());
            if (s.map(x -> x.getEstado().permiteAcceso()).orElse(false)) {
                resultado.add(new EmpresaEntrableResponse(e.getId(), e.getRazonSocial(),
                        s.get().getEstado().name()));
            }
        }
        return resultado;
    }

    /**
     * Entra a una empresa del grupo: verifica que sea del grupo del token y que el super administrador tenga su usuario
     * vinculado en ella, y emite una sesión normal de esa empresa (JWT y refresh token) para ese usuario Dueño. La
     * entrada queda auditada en la empresa.
     *
     * @param empresaId empresa a la que entra
     * @return sesión de la empresa, igual que un login normal
     * @throws NoEncontradoException si la empresa no existe, es de otro grupo o no hay vínculo con usuario activo
     */
    public LoginResponse entrar(Long empresaId) {
        GrupoAutenticado u = GrupoActual.usuario();
        Empresa empresa = empresaRepository.findById(empresaId).filter(e -> u.grupoId().equals(e.getGrupoId()))
                .orElseThrow(() -> new NoEncontradoException(EMPRESA_NO_EXISTE));
        UsuarioGrupoEmpresa vinculo = vinculoRepository.findByUsuarioGrupoIdAndEmpresaId(u.id(), empresa.getId())
                .orElseThrow(() -> new NoEncontradoException(EMPRESA_NO_EXISTE));
        return EmpresaContexto.como(empresa.getId(),
                () -> authService.iniciarSesionDeGrupo(vinculo.getUsuarioId(), u.id()));
    }
}
