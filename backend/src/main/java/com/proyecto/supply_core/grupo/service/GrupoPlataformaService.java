package com.proyecto.supply_core.grupo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.AsignacionGrupoResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.GrupoResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.SuperAdminCrearRequest;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.SuperAdminResponse;
import com.proyecto.supply_core.grupo.entity.GrupoEmpresa;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupo;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupoEmpresa;
import com.proyecto.supply_core.grupo.repository.GrupoEmpresaRepository;
import com.proyecto.supply_core.grupo.repository.TrasladoGrupoRepository;
import com.proyecto.supply_core.grupo.repository.UsuarioGrupoEmpresaRepository;
import com.proyecto.supply_core.grupo.repository.UsuarioGrupoRepository;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.service.PlataformaAuditoriaService;
import com.proyecto.supply_core.security.context.PlataformaActual;

/**
 * Grupos de empresas en la consola de la plataforma (spec 035): crear grupos, asignar o quitar empresas y crear al
 * super administrador. La plataforma sigue sin leer datos de negocio: solo toca el grupo de cada empresa y, para
 * vincular al super administrador, crea o desactiva un usuario dentro de cada empresa con {@code EmpresaContexto.como}.
 * <p>No es transaccional por sí mismo (igual que el alta de empresas): cada paso abre su transacción en la empresa que
 * le toca, y si algo falla a mitad de camino se deshace lo ya hecho.</p>
 */
@Service
public class GrupoPlataformaService {

    private static final Logger log = LoggerFactory.getLogger(GrupoPlataformaService.class);

    private final GrupoEmpresaRepository grupoRepository;
    private final UsuarioGrupoRepository usuarioGrupoRepository;
    private final UsuarioGrupoEmpresaRepository vinculoRepository;
    private final EmpresaRepository empresaRepository;
    private final TrasladoGrupoRepository trasladoRepository;
    private final GrupoVinculoService vinculoService;
    private final PasswordEncoder passwordEncoder;
    private final PlataformaAuditoriaService auditoria;

    /**
     * @param grupoRepository        grupos
     * @param usuarioGrupoRepository super administradores
     * @param vinculoRepository      vínculos con las empresas
     * @param empresaRepository      empresas
     * @param trasladoRepository     traslados entre empresas (no se saca del grupo a una con traslados en tránsito)
     * @param vinculoService         usuarios vinculados dentro de cada empresa
     * @param passwordEncoder        hash BCrypt de la clave del super administrador
     * @param auditoria              auditoría global
     */
    public GrupoPlataformaService(GrupoEmpresaRepository grupoRepository, UsuarioGrupoRepository usuarioGrupoRepository,
            UsuarioGrupoEmpresaRepository vinculoRepository, EmpresaRepository empresaRepository,
            TrasladoGrupoRepository trasladoRepository,
            GrupoVinculoService vinculoService, PasswordEncoder passwordEncoder,
            PlataformaAuditoriaService auditoria) {
        this.grupoRepository = grupoRepository;
        this.usuarioGrupoRepository = usuarioGrupoRepository;
        this.vinculoRepository = vinculoRepository;
        this.empresaRepository = empresaRepository;
        this.trasladoRepository = trasladoRepository;
        this.vinculoService = vinculoService;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
    }

    /**
     * Lista los grupos con sus empresas y sus super administradores.
     *
     * @return grupos, el más nuevo primero
     */
    @Transactional(readOnly = true)
    public List<GrupoResponse> listar() {
        Map<Long, List<Empresa>> empresas = empresaRepository.findAll().stream().filter(e -> e.getGrupoId() != null)
                .collect(Collectors.groupingBy(Empresa::getGrupoId));
        Map<Long, List<UsuarioGrupo>> admins = usuarioGrupoRepository.findAll().stream()
                .collect(Collectors.groupingBy(UsuarioGrupo::getGrupoId));
        return grupoRepository.findAll().stream().sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .map(g -> GrupoResponse.of(g, empresas.getOrDefault(g.getId(), List.of()),
                        admins.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    /**
     * Crea un grupo vacío.
     *
     * @param nombre nombre del grupo
     * @return grupo creado
     * @throws NegocioException {@code GRUPO_DUPLICADO} si ya hay uno con ese nombre
     */
    public GrupoResponse crear(String nombre) {
        String limpio = nombre.trim().replaceAll("\\s+", " ");
        if (grupoRepository.existsByNombreIgnoreCase(limpio)) {
            throw new NegocioException("GRUPO_DUPLICADO", "Ya existe un grupo llamado " + limpio);
        }
        GrupoEmpresa g = new GrupoEmpresa();
        g.setNombre(limpio);
        GrupoEmpresa guardado = grupoRepository.save(g);
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.GRUPO_CREAR, null, true,
                "grupo=" + guardado.getId() + "; nombre=" + limpio);
        return GrupoResponse.of(guardado, List.of(), List.of());
    }

    /**
     * Asigna una empresa a un grupo, la cambia de grupo o la saca de él. Al entrar a un grupo, cada super administrador
     * activo queda vinculado con un usuario Dueño dentro de la empresa; al salir (o cambiar), esos usuarios se
     * desactivan y sus sesiones se cortan. Repetir la asignación al mismo grupo repara los vínculos que falten.
     *
     * @param empresaId empresa
     * @param grupoId   grupo nuevo; {@code null} la saca de su grupo
     * @return la empresa y su grupo actual
     * @throws NoEncontradoException si la empresa o el grupo no existen
     * @throws NegocioException      {@code TRASLADOS_EN_TRANSITO} si sale de su grupo con traslados en tránsito;
     *                               {@code CORREO_EN_USO_EMPRESA} si algún correo de super administrador ya lo usa otro
     *                               usuario de la empresa (no se cambia nada)
     */
    public AsignacionGrupoResponse asignarEmpresa(Long empresaId, Long grupoId) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new NoEncontradoException("Empresa", empresaId));
        GrupoEmpresa nuevo = grupoId == null ? null : grupoRepository.findById(grupoId)
                .orElseThrow(() -> new NoEncontradoException("Grupo", grupoId));
        if (nuevo != null && !nuevo.isActivo()) {
            throw new NegocioException("GRUPO_INACTIVO", "El grupo " + nuevo.getNombre() + " está inactivo");
        }
        Long anterior = empresa.getGrupoId();
        if (anterior != null && !anterior.equals(grupoId) && trasladoRepository.hayEnTransito(empresaId)) {
            throw new NegocioException("TRASLADOS_EN_TRANSITO", "La empresa tiene traslados en tránsito como origen o destino: "
                    + "recíbalos o anúlelos antes de sacarla del grupo o pasarla a otro");
        }
        List<UsuarioGrupo> entrantes = nuevo == null ? List.of() : usuarioGrupoRepository
                .findByGrupoIdOrderByIdAsc(nuevo.getId()).stream().filter(UsuarioGrupo::isActivo).toList();
        // todo o nada: primero se comprueba que ningún correo choque dentro de la empresa
        if (nuevo != null) {
            entrantes.forEach(u -> EmpresaContexto.como(empresaId,
                    () -> vinculoService.exigirCorreoLibre(u.getId(), empresaId, u.getCorreo())));
        }
        if (anterior != null && !anterior.equals(grupoId)) {
            usuarioGrupoRepository.findByGrupoIdOrderByIdAsc(anterior)
                    .forEach(u -> EmpresaContexto.como(empresaId, () -> vinculoService.desvincular(u.getId(), empresaId)));
        }
        empresa.setGrupoId(grupoId);
        empresaRepository.save(empresa);
        entrantes.forEach(u -> EmpresaContexto.como(empresaId,
                () -> vinculoService.vincular(u.getId(), empresaId, u.getNombre(), u.getCorreo())));
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.GRUPO_ASIGNAR_EMPRESA, empresaId, true,
                "grupoAnterior=" + anterior + "; grupoNuevo=" + grupoId);
        return new AsignacionGrupoResponse(empresaId, grupoId);
    }

    /**
     * Crea al super administrador de un grupo y lo vincula, con un usuario Dueño, dentro de cada empresa del grupo. Su
     * clave es la que escribe quien administra la plataforma; el segundo factor se configura en su primer ingreso. Si
     * algo falla a mitad de camino se deja desactivado y sin vínculos activos.
     *
     * @param grupoId grupo
     * @param req     nombre, correo y clave
     * @return super administrador creado
     * @throws NoEncontradoException si el grupo no existe
     * @throws NegocioException      {@code CORREO_DUPLICADO} si el correo ya es de otro super administrador, o
     *                               {@code CORREO_EN_USO_EMPRESA} si algún usuario de las empresas ya lo usa
     */
    public SuperAdminResponse crearSuperAdmin(Long grupoId, SuperAdminCrearRequest req) {
        GrupoEmpresa grupo = grupoRepository.findById(grupoId).orElseThrow(() -> new NoEncontradoException("Grupo", grupoId));
        if (!grupo.isActivo()) {
            throw new NegocioException("GRUPO_INACTIVO", "El grupo " + grupo.getNombre() + " está inactivo");
        }
        String correo = req.correo().trim().toLowerCase(Locale.ROOT);
        if (usuarioGrupoRepository.existsByCorreoIgnoreCase(correo)) {
            throw new NegocioException("CORREO_DUPLICADO", "Ya existe un super administrador con el correo " + correo);
        }
        List<Empresa> empresas = empresaRepository.findByGrupoIdOrderByIdAsc(grupoId);
        empresas.forEach(e -> EmpresaContexto.como(e.getId(), () -> vinculoService.exigirCorreoLibre(null, e.getId(), correo)));

        UsuarioGrupo u = new UsuarioGrupo();
        u.setGrupoId(grupoId);
        u.setNombre(req.nombre().trim());
        u.setCorreo(correo);
        u.setHashPassword(passwordEncoder.encode(req.password()));
        UsuarioGrupo guardado = usuarioGrupoRepository.saveAndFlush(u);
        try {
            empresas.forEach(e -> EmpresaContexto.como(e.getId(),
                    () -> vinculoService.vincular(guardado.getId(), e.getId(), guardado.getNombre(), correo)));
        } catch (RuntimeException ex) {
            log.error("Falló la creación del super administrador {} del grupo {}; se desactiva: {}", guardado.getId(),
                    grupoId, ex.getMessage(), ex);
            desactivar(guardado, empresas);
            auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.GRUPO_SUPER_ADMIN_CREAR, null, false,
                    "grupo=" + grupoId + "; correo=" + correo + "; motivo=" + ex.getMessage());
            throw ex;
        }
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.GRUPO_SUPER_ADMIN_CREAR, null, true,
                "grupo=" + grupoId + "; usuarioGrupo=" + guardado.getId() + "; correo=" + correo + "; empresas="
                        + empresas.stream().map(Empresa::getId).toList());
        return SuperAdminResponse.of(guardado);
    }

    /**
     * Activa o desactiva a un super administrador. Al desactivarlo se cortan sus sesiones de grupo (sube su versión de
     * sesión) y se desactivan sus usuarios Dueño dentro de cada empresa, con sus sesiones. Al activarlo se vuelven a
     * activar o crear esos usuarios en las empresas actuales del grupo.
     *
     * @param grupoId grupo al que debe pertenecer
     * @param id      super administrador
     * @param activo  estado nuevo
     * @return super administrador con su estado actual
     * @throws NoEncontradoException si no existe o no es de ese grupo
     */
    public SuperAdminResponse cambiarEstado(Long grupoId, Long id, boolean activo) {
        UsuarioGrupo u = usuarioGrupoRepository.findById(id).filter(x -> x.getGrupoId().equals(grupoId))
                .orElseThrow(() -> new NoEncontradoException("Super administrador", id));
        List<Empresa> empresas = empresaRepository.findByGrupoIdOrderByIdAsc(grupoId);
        if (activo) {
            empresas.forEach(e -> EmpresaContexto.como(e.getId(), () -> vinculoService.exigirCorreoLibre(u.getId(), e.getId(),
                    u.getCorreo())));
            u.setActivo(true);
            u.setIntentosFallidos(0);
            u.setBloqueadoHasta(null);
            usuarioGrupoRepository.save(u);
            empresas.forEach(e -> EmpresaContexto.como(e.getId(),
                    () -> vinculoService.vincular(u.getId(), e.getId(), u.getNombre(), u.getCorreo())));
        } else {
            desactivar(u, empresas);
        }
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.GRUPO_SUPER_ADMIN_ESTADO, null, true,
                "grupo=" + grupoId + "; usuarioGrupo=" + id + "; activo=" + activo);
        return SuperAdminResponse.of(u);
    }

    /**
     * Reinicia el segundo factor de un super administrador (perdió el teléfono): borra el secreto, de modo que en su
     * siguiente ingreso vuelve a configurarlo, y sube su versión de sesión para cortar las que tenga abiertas, tanto las
     * del grupo como las de empresa abiertas con {@code /api/grupo/entrar} (sin desactivar a sus usuarios vinculados).
     *
     * @param grupoId grupo al que debe pertenecer
     * @param id      super administrador
     * @return super administrador con su estado actual
     * @throws NoEncontradoException si no existe o no es de ese grupo
     */
    public SuperAdminResponse reiniciarMfa(Long grupoId, Long id) {
        UsuarioGrupo u = usuarioGrupoRepository.findById(id).filter(x -> x.getGrupoId().equals(grupoId))
                .orElseThrow(() -> new NoEncontradoException("Super administrador", id));
        u.setMfaSecreto(null);
        u.setMfaActivo(false);
        u.setMfaUltimoPaso(null);
        u.setVersionSesion(u.getVersionSesion() + 1);
        usuarioGrupoRepository.save(u);
        // también las sesiones de empresa abiertas con /api/grupo/entrar (sin desactivar al usuario vinculado)
        vinculoRepository.findByUsuarioGrupoId(u.getId()).stream().map(UsuarioGrupoEmpresa::getEmpresaId)
                .forEach(eid -> EmpresaContexto.como(eid, () -> vinculoService.cortarSesiones(u.getId(), eid)));
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.GRUPO_SUPER_ADMIN_REINICIAR_MFA, null, true,
                "grupo=" + grupoId + "; usuarioGrupo=" + id + "; correo=" + u.getCorreo());
        return SuperAdminResponse.of(u);
    }

    /**
     * Desactiva al super administrador, sube su versión de sesión y desactiva sus usuarios dentro de las empresas (todas
     * las que tengan vínculo, aunque ya no sean del grupo).
     *
     * @param u        super administrador
     * @param empresas empresas actuales del grupo
     */
    private void desactivar(UsuarioGrupo u, List<Empresa> empresas) {
        u.setActivo(false);
        u.setVersionSesion(u.getVersionSesion() + 1);
        usuarioGrupoRepository.save(u);
        List<Long> ids = new ArrayList<>(empresas.stream().map(Empresa::getId).toList());
        vinculoRepository.findByUsuarioGrupoId(u.getId()).stream().map(UsuarioGrupoEmpresa::getEmpresaId)
                .filter(id -> !ids.contains(id)).forEach(ids::add);
        ids.forEach(id -> EmpresaContexto.como(id, () -> vinculoService.desvincular(u.getId(), id)));
    }
}
