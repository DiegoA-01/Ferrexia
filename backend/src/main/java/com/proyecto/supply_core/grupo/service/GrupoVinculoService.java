package com.proyecto.supply_core.grupo.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupoEmpresa;
import com.proyecto.supply_core.grupo.repository.UsuarioGrupoEmpresaRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.service.SesionUsuarioService;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Crea, reactiva y desactiva, dentro de una empresa, el usuario Dueño (rol ADMIN_SISTEMA) con el que un super
 * administrador de grupo trabaja en ella. Está en un bean aparte porque cada método debe abrir su transacción ya dentro
 * de la empresa: se llama siempre dentro de {@code EmpresaContexto.como(empresaId, ...)}.
 * <p>Ese usuario nunca entra por el login normal: su clave es aleatoria y nadie la conoce, y la recuperación de clave
 * lo ignora. Solo se llega a él desde {@code /api/grupo/entrar/{empresaId}}.</p>
 */
@Service
public class GrupoVinculoService {

    private static final SecureRandom AZAR = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioGrupoEmpresaRepository vinculoRepository;
    private final SesionUsuarioService sesiones;

    /**
     * @param usuarioRepository usuarios de la empresa del contexto
     * @param rolRepository     rol ADMIN_SISTEMA
     * @param passwordEncoder   hash BCrypt de la clave aleatoria
     * @param vinculoRepository vínculos entre super administradores y empresas
     * @param sesiones          corte de sesiones al desactivar al usuario
     */
    public GrupoVinculoService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
            PasswordEncoder passwordEncoder, UsuarioGrupoEmpresaRepository vinculoRepository,
            SesionUsuarioService sesiones) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.vinculoRepository = vinculoRepository;
        this.sesiones = sesiones;
    }

    /**
     * Verifica que el correo se pueda usar para vincular al super administrador en la empresa del contexto: o no lo usa
     * nadie, o ya es el usuario vinculado de ese mismo super administrador.
     *
     * @param usuarioGrupoId super administrador (puede ser {@code null} si aún no existe)
     * @param empresaId      empresa del contexto
     * @param correo         correo ya normalizado
     * @throws NegocioException {@code CORREO_EN_USO_EMPRESA} si otro usuario de la empresa tiene ese correo
     */
    @Transactional(readOnly = true)
    public void exigirCorreoLibre(Long usuarioGrupoId, Long empresaId, String correo) {
        Optional<UsuarioGrupoEmpresa> vinculo = usuarioGrupoId == null ? Optional.empty()
                : vinculoRepository.findByUsuarioGrupoIdAndEmpresaId(usuarioGrupoId, empresaId);
        if (vinculo.isPresent()) {
            return;
        }
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new NegocioException("CORREO_EN_USO_EMPRESA",
                    "El correo " + correo + " ya lo usa un usuario de la empresa " + empresaId
                            + "; el super administrador necesita un correo libre en cada empresa del grupo");
        }
    }

    /**
     * Deja al super administrador vinculado y activo en la empresa del contexto: crea su usuario Dueño o, si ya lo tenía
     * de antes, lo reactiva. Es idempotente.
     *
     * @param usuarioGrupoId super administrador
     * @param empresaId      empresa del contexto
     * @param nombre         nombre completo
     * @param correo         correo ya normalizado
     * @return id del usuario Dueño dentro de la empresa
     * @throws NegocioException {@code CORREO_EN_USO_EMPRESA} si otro usuario de la empresa ya tiene ese correo
     */
    @Transactional
    public Long vincular(Long usuarioGrupoId, Long empresaId, String nombre, String correo) {
        Optional<UsuarioGrupoEmpresa> vinculo = vinculoRepository.findByUsuarioGrupoIdAndEmpresaId(usuarioGrupoId,
                empresaId);
        if (vinculo.isPresent()) {
            Usuario u = usuarioRepository.findWithRolesById(vinculo.get().getUsuarioId())
                    .orElseThrow(() -> new IllegalStateException("Falta el usuario vinculado " + vinculo.get().getUsuarioId()));
            u.setActivo(true);
            u.setNombreCompleto(nombre.trim());
            u.setIntentosFallidos(0);
            u.setBloqueadoHasta(null);
            if (!u.codigosRoles().contains(Roles.ADMIN_SISTEMA)) {
                u.getRoles().add(rolRepository.findByCodigo(Roles.ADMIN_SISTEMA)
                        .orElseThrow(() -> new IllegalStateException("Falta el rol ADMIN_SISTEMA (V2)")));
            }
            return u.getId();
        }
        exigirCorreoLibre(usuarioGrupoId, empresaId, correo);
        Usuario u = new Usuario();
        u.setNombreCompleto(nombre.trim());
        u.setCorreo(correo.trim().toLowerCase(Locale.ROOT));
        u.setHashPassword(passwordEncoder.encode(claveAleatoria()));
        u.getRoles().add(rolRepository.findByCodigo(Roles.ADMIN_SISTEMA)
                .orElseThrow(() -> new IllegalStateException("Falta el rol ADMIN_SISTEMA (V2)")));
        Usuario guardado = usuarioRepository.saveAndFlush(u);
        vinculoRepository.save(new UsuarioGrupoEmpresa(usuarioGrupoId, empresaId, guardado.getId()));
        return guardado.getId();
    }

    /**
     * Desactiva el usuario vinculado del super administrador en la empresa del contexto y corta sus sesiones (sube su
     * versión de sesión y revoca sus refresh tokens). Si no hay vínculo no hace nada. El vínculo se conserva para poder
     * reactivar al mismo usuario después.
     *
     * @param usuarioGrupoId super administrador
     * @param empresaId      empresa del contexto
     */
    @Transactional
    public void desvincular(Long usuarioGrupoId, Long empresaId) {
        vinculoRepository.findByUsuarioGrupoIdAndEmpresaId(usuarioGrupoId, empresaId)
                .flatMap(v -> usuarioRepository.findWithRolesById(v.getUsuarioId())).ifPresent(u -> {
                    u.setActivo(false);
                    sesiones.revocarSesiones(u);
                });
    }

    /**
     * Corta las sesiones de empresa del usuario vinculado del super administrador (sube su versión de sesión y revoca sus
     * refresh tokens) sin desactivarlo. Si no hay vínculo no hace nada.
     *
     * @param usuarioGrupoId super administrador
     * @param empresaId      empresa del contexto
     */
    @Transactional
    public void cortarSesiones(Long usuarioGrupoId, Long empresaId) {
        vinculoRepository.findByUsuarioGrupoIdAndEmpresaId(usuarioGrupoId, empresaId)
                .flatMap(v -> usuarioRepository.findWithRolesById(v.getUsuarioId()))
                .ifPresent(sesiones::revocarSesiones);
    }

    /**
     * Clave aleatoria de 256 bits que se guarda con hash y se descarta: nadie puede iniciar sesión con ella.
     *
     * @return texto aleatorio
     */
    private static String claveAleatoria() {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
