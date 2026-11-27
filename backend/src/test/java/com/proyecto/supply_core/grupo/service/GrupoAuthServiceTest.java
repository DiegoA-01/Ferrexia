package com.proyecto.supply_core.grupo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.LoginGrupoRequest;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.MfaGrupoRequest;
import com.proyecto.supply_core.grupo.entity.GrupoEmpresa;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupo;
import com.proyecto.supply_core.grupo.repository.GrupoEmpresaRepository;
import com.proyecto.supply_core.grupo.repository.UsuarioGrupoRepository;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.service.PlataformaAuditoriaService;
import com.proyecto.supply_core.plataforma.service.TotpService;
import com.proyecto.supply_core.security.jwt.JwtProperties;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.security.jwt.SesionProperties;

/** Login del super administrador de un grupo con MFA obligatorio (spec 035). */
class GrupoAuthServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // costo bajo: prueba rápida
    private final UsuarioGrupoRepository repo = mock(UsuarioGrupoRepository.class);
    private final GrupoEmpresaRepository grupos = mock(GrupoEmpresaRepository.class);
    private final PlataformaAuditoriaService auditoria = mock(PlataformaAuditoriaService.class);
    private final TotpService totp = new TotpService();
    private final JwtUtil jwt = new JwtUtil(new JwtProperties("clave-de-prueba-de-al-menos-32-caracteres!!", 15));
    private GrupoAuthService service;
    private UsuarioGrupo superAdmin;
    private GrupoEmpresa grupo;

    @BeforeEach
    void setUp() {
        service = new GrupoAuthService(repo, grupos, encoder, jwt, totp, auditoria, new SesionProperties(7, 3, 15, 20));
        grupo = new GrupoEmpresa();
        grupo.setId(2L);
        grupo.setNombre("Ferrecentro");
        superAdmin = new UsuarioGrupo();
        superAdmin.setId(7L);
        superAdmin.setGrupoId(2L);
        superAdmin.setCorreo("super@grupo.com");
        superAdmin.setNombre("Super Admin");
        superAdmin.setVersionSesion(3);
        superAdmin.setHashPassword(encoder.encode("ClaveSegura1234"));
        when(repo.findByCorreoIgnoreCase("super@grupo.com")).thenReturn(Optional.of(superAdmin));
        when(repo.findById(7L)).thenReturn(Optional.of(superAdmin));
        when(grupos.findById(2L)).thenReturn(Optional.of(grupo));
    }

    @Test
    void laPrimeraVezEntregaElSecretoParaVincularLaAppYUnDesafioDeGrupo() {
        var r = service.login(new LoginGrupoRequest(" SUPER@grupo.com ", "ClaveSegura1234"));

        assertFalse(r.mfaConfigurado());
        assertNotNull(r.secretoMfa());
        assertEquals(superAdmin.getMfaSecreto(), r.secretoMfa());
        assertEquals(7L, jwt.validarDesafioMfaGrupo(r.desafio()));
        verify(auditoria).registrar(isNull(), eq(AccionPlataforma.GRUPO_LOGIN), isNull(), eq(true), anyString());
    }

    @Test
    void conElMfaYaConfiguradoNoVuelveARevelarElSecreto() {
        superAdmin.setMfaSecreto(totp.nuevoSecreto());
        superAdmin.setMfaActivo(true);

        var r = service.login(new LoginGrupoRequest("super@grupo.com", "ClaveSegura1234"));

        assertTrue(r.mfaConfigurado());
        assertNull(r.secretoMfa());
        assertNull(r.otpauthUri());
    }

    @Test
    void claveMalaCorreoInexistenteGrupoInactivoOCuentaInactivaRespondenIgual() {
        var malaClave = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginGrupoRequest("super@grupo.com", "otra")));
        var sinCorreo = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginGrupoRequest("nadie@grupo.com", "ClaveSegura1234")));
        grupo.setActivo(false);
        var grupoInactivo = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginGrupoRequest("super@grupo.com", "ClaveSegura1234")));
        grupo.setActivo(true);
        superAdmin.setActivo(false);
        var inactivo = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginGrupoRequest("super@grupo.com", "ClaveSegura1234")));

        assertEquals(GrupoAuthService.CREDENCIALES_INVALIDAS, malaClave.getMessage());
        assertEquals(malaClave.getMessage(), sinCorreo.getMessage());
        assertEquals(malaClave.getMessage(), grupoInactivo.getMessage());
        assertEquals(malaClave.getMessage(), inactivo.getMessage());
    }

    @Test
    void tresClavesMalasBloqueanLaCuentaAunqueLaSiguienteSeaCorrecta() {
        for (int i = 0; i < 3; i++) {
            assertThrows(NoAutenticadoException.class,
                    () -> service.login(new LoginGrupoRequest("super@grupo.com", "mala")));
        }
        assertTrue(superAdmin.getBloqueadoHasta().isAfter(LocalDateTime.now()));

        var ex = assertThrows(NoAutenticadoException.class,
                () -> service.login(new LoginGrupoRequest("super@grupo.com", "ClaveSegura1234")));
        assertEquals(GrupoAuthService.CREDENCIALES_INVALIDAS, ex.getMessage());
    }

    @Test
    void conElCodigoCorrectoEntregaLaSesionDeGrupoConSuVersionYConfirmaElMfa() {
        superAdmin.setMfaSecreto(totp.nuevoSecreto());
        String desafio = jwt.generarDesafioMfaGrupo(7L);
        String codigo = totp.codigo(superAdmin.getMfaSecreto(), totp.paso(Instant.now()));

        var sesion = service.verificarMfa(new MfaGrupoRequest(desafio, codigo));

        assertEquals(2L, sesion.grupoId());
        assertEquals("Ferrecentro", sesion.grupoNombre());
        assertEquals(7L, jwt.validarGrupo(sesion.token()).id());
        assertEquals(3, jwt.versionSesion(sesion.token()));
        assertTrue(superAdmin.isMfaActivo());
        assertNotNull(superAdmin.getMfaUltimoPaso());
    }

    @Test
    void unCodigoNoSeAceptaDosVecesNiUnoIncorrecto() {
        superAdmin.setMfaSecreto(totp.nuevoSecreto());
        String desafio = jwt.generarDesafioMfaGrupo(7L);
        String codigo = totp.codigo(superAdmin.getMfaSecreto(), totp.paso(Instant.now()));
        service.verificarMfa(new MfaGrupoRequest(desafio, codigo));

        assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaGrupoRequest(desafio, codigo)));
        assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaGrupoRequest(desafio, "000000")));
    }

    @Test
    void unDesafioDeLaPlataformaOUnaSesionNoSirvenComoDesafioDelGrupo() {
        superAdmin.setMfaSecreto(totp.nuevoSecreto());
        String codigo = totp.codigo(superAdmin.getMfaSecreto(), totp.paso(Instant.now()));

        assertThrows(NoAutenticadoException.class,
                () -> service.verificarMfa(new MfaGrupoRequest(jwt.generarDesafioMfa(7L), codigo)));
        assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaGrupoRequest(
                jwt.generarGrupo(new com.proyecto.supply_core.security.context.GrupoAutenticado(7L, 2L, "x", "x"), 0),
                codigo)));
    }

    @Test
    void conElGrupoOLaCuentaDesactivadosElCodigoNoDaSesion() {
        superAdmin.setMfaSecreto(totp.nuevoSecreto());
        String desafio = jwt.generarDesafioMfaGrupo(7L);
        String codigo = totp.codigo(superAdmin.getMfaSecreto(), totp.paso(Instant.now()));
        grupo.setActivo(false);

        assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaGrupoRequest(desafio, codigo)));
        grupo.setActivo(true);
        superAdmin.setActivo(false);
        assertThrows(NoAutenticadoException.class, () -> service.verificarMfa(new MfaGrupoRequest(desafio, codigo)));
    }
}
