package com.proyecto.supply_core.grupo.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.LoginGrupoRequest;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.MfaGrupoRequest;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.SesionGrupoResponse;
import com.proyecto.supply_core.grupo.entity.GrupoEmpresa;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupo;
import com.proyecto.supply_core.grupo.repository.GrupoEmpresaRepository;
import com.proyecto.supply_core.grupo.repository.UsuarioGrupoRepository;
import com.proyecto.supply_core.plataforma.dto.DesafioMfaResponse;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.service.PlataformaAuditoriaService;
import com.proyecto.supply_core.plataforma.service.TotpService;
import com.proyecto.supply_core.security.context.GrupoAutenticado;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.security.jwt.SesionProperties;

/**
 * Login del super administrador de un grupo en dos pasos, con MFA obligatorio (mismo molde que el de la plataforma):
 * contraseña (que entrega un desafío de 5 minutos y, la primera vez, el secreto para vincular la app) y código de MFA
 * (que entrega la sesión de grupo). Un correo inexistente, una clave mala, una cuenta bloqueada o un grupo inactivo
 * responden igual y tardan igual; los fallos suman a un contador que bloquea la cuenta. Todo queda en la auditoría
 * global (sin usuario de plataforma: el super administrador va en el detalle).
 */
@Service
public class GrupoAuthService {

    private static final Logger log = LoggerFactory.getLogger(GrupoAuthService.class);
    /** Mensaje único para correo inexistente, clave errada, cuenta bloqueada o grupo inactivo. */
    static final String CREDENCIALES_INVALIDAS = "Credenciales inválidas";
    /** Mensaje para un código de MFA que no sirve. */
    static final String CODIGO_INVALIDO = "Código inválido";

    private final UsuarioGrupoRepository usuarioRepository;
    private final GrupoEmpresaRepository grupoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TotpService totpService;
    private final PlataformaAuditoriaService auditoria;
    private final SesionProperties sesionProperties;
    /** Hash de relleno: se compara aunque el correo no exista para que ambos casos tarden igual. */
    private final String hashFicticio;

    /**
     * @param usuarioRepository super administradores
     * @param grupoRepository   grupos
     * @param passwordEncoder   verificador BCrypt
     * @param jwtUtil           emisor de desafíos y sesiones
     * @param totpService       códigos de un solo uso
     * @param auditoria         auditoría global
     * @param sesionProperties  intentos máximos y duración del bloqueo
     */
    public GrupoAuthService(UsuarioGrupoRepository usuarioRepository, GrupoEmpresaRepository grupoRepository,
            PasswordEncoder passwordEncoder, JwtUtil jwtUtil, TotpService totpService,
            PlataformaAuditoriaService auditoria, SesionProperties sesionProperties) {
        this.usuarioRepository = usuarioRepository;
        this.grupoRepository = grupoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.totpService = totpService;
        this.auditoria = auditoria;
        this.sesionProperties = sesionProperties;
        this.hashFicticio = passwordEncoder.encode("supply-core-relleno-de-tiempo-grupo");
    }

    /**
     * Primer paso: verifica la contraseña y entrega el desafío de MFA.
     *
     * @param req correo y contraseña ya validados en formato
     * @return desafío y, si falta vincular la app, el secreto para hacerlo
     * @throws NoAutenticadoException si las credenciales no sirven o la cuenta está bloqueada o inactiva
     */
    @Transactional(noRollbackFor = NoAutenticadoException.class) // el contador de fallos debe quedar guardado
    public DesafioMfaResponse login(LoginGrupoRequest req) {
        String correo = req.correo().trim().toLowerCase(Locale.ROOT);
        UsuarioGrupo u = usuarioRepository.findByCorreoIgnoreCase(correo).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        boolean claveCorrecta = passwordEncoder.matches(req.password(), u == null ? hashFicticio : u.getHashPassword());
        boolean grupoActivo = u != null && grupoRepository.findById(u.getGrupoId()).map(GrupoEmpresa::isActivo).orElse(false);

        if (u == null || !claveCorrecta || estaBloqueado(u, ahora) || !u.isActivo() || !grupoActivo) {
            Long id = u == null ? null : u.getId();
            String motivo = u == null ? "correo no registrado" : !claveCorrecta ? "contraseña incorrecta"
                    : estaBloqueado(u, ahora) ? "cuenta bloqueada" : !u.isActivo() ? "cuenta inactiva" : "grupo inactivo";
            log.info("Login de grupo fallido para {} ({})", correo, motivo);
            auditoria.registrar(null, AccionPlataforma.GRUPO_LOGIN, null, false,
                    "usuarioGrupo=" + id + "; correo=" + correo + "; motivo=" + motivo);
            if (u != null && !claveCorrecta && !estaBloqueado(u, ahora)) {
                registrarFallo(u, ahora);
            }
            throw new NoAutenticadoException(CREDENCIALES_INVALIDAS);
        }
        boolean configurado = u.isMfaActivo();
        if (u.getMfaSecreto() == null) {
            u.setMfaSecreto(totpService.nuevoSecreto());
        }
        auditoria.registrar(null, AccionPlataforma.GRUPO_LOGIN, null, true,
                "usuarioGrupo=" + u.getId() + "; correo=" + correo + "; mfaConfigurado=" + configurado);
        String desafio = jwtUtil.generarDesafioMfaGrupo(u.getId());
        return configurado ? new DesafioMfaResponse(desafio, true, null, null)
                : new DesafioMfaResponse(desafio, false, u.getMfaSecreto(), totpService.uri(u.getCorreo(), u.getMfaSecreto()));
    }

    /**
     * Segundo paso: verifica el código de MFA y entrega la sesión de grupo. El primer código válido confirma el MFA; un
     * código no se acepta dos veces.
     *
     * @param req desafío y código ya validados en formato
     * @return sesión de grupo
     * @throws NoAutenticadoException si el desafío o el código no sirven, o la cuenta o el grupo no están disponibles
     */
    @Transactional(noRollbackFor = NoAutenticadoException.class)
    public SesionGrupoResponse verificarMfa(MfaGrupoRequest req) {
        Long id = jwtUtil.validarDesafioMfaGrupo(req.desafio());
        UsuarioGrupo u = usuarioRepository.findById(id).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        GrupoEmpresa grupo = u == null ? null : grupoRepository.findById(u.getGrupoId()).orElse(null);
        if (u == null || !u.isActivo() || grupo == null || !grupo.isActivo() || estaBloqueado(u, ahora)
                || u.getMfaSecreto() == null) {
            auditoria.registrar(null, AccionPlataforma.GRUPO_MFA, null, false,
                    "usuarioGrupo=" + id + "; motivo=cuenta no disponible");
            throw new NoAutenticadoException(CODIGO_INVALIDO);
        }
        OptionalLong paso = totpService.verificar(u.getMfaSecreto(), req.codigo(), u.getMfaUltimoPaso(), Instant.now());
        if (paso.isEmpty()) {
            auditoria.registrar(null, AccionPlataforma.GRUPO_MFA, null, false,
                    "usuarioGrupo=" + id + "; motivo=código incorrecto o ya usado");
            registrarFallo(u, ahora);
            throw new NoAutenticadoException(CODIGO_INVALIDO);
        }
        u.setMfaUltimoPaso(paso.getAsLong());
        u.setMfaActivo(true);
        u.setUltimoAcceso(ahora);
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
        auditoria.registrar(null, AccionPlataforma.GRUPO_MFA, null, true,
                "usuarioGrupo=" + id + "; correo=" + u.getCorreo());
        String token = jwtUtil.generarGrupo(new GrupoAutenticado(u.getId(), u.getGrupoId(), u.getCorreo(), u.getNombre()),
                u.getVersionSesion());
        return new SesionGrupoResponse(token, "Bearer", jwtUtil.getExpiracionSegundos(), u.getId(), u.getCorreo(),
                u.getNombre(), grupo.getId(), grupo.getNombre());
    }

    /**
     * Indica si la cuenta sigue bloqueada.
     *
     * @param u     super administrador
     * @param ahora momento actual
     * @return {@code true} si el bloqueo no ha terminado
     */
    private static boolean estaBloqueado(UsuarioGrupo u, LocalDateTime ahora) {
        return u.getBloqueadoHasta() != null && u.getBloqueadoHasta().isAfter(ahora);
    }

    /**
     * Suma un fallo (de contraseña o de código) y bloquea la cuenta al llegar al máximo de intentos seguidos.
     *
     * @param u     super administrador que falló
     * @param ahora momento del intento
     */
    private void registrarFallo(UsuarioGrupo u, LocalDateTime ahora) {
        int fallos = u.getIntentosFallidos() + 1;
        if (fallos >= sesionProperties.maxIntentos()) {
            u.setIntentosFallidos(0);
            u.setBloqueadoHasta(ahora.plusMinutes(sesionProperties.bloqueoMinutos()));
            auditoria.registrar(null, AccionPlataforma.GRUPO_LOGIN, null, false,
                    "usuarioGrupo=" + u.getId() + "; cuenta bloqueada " + sesionProperties.bloqueoMinutos()
                            + " min tras " + fallos + " intentos");
        } else {
            u.setIntentosFallidos(fallos);
        }
    }
}
