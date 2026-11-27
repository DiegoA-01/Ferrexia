package com.proyecto.supply_core.grupo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.SuperAdminCrearRequest;
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
import com.proyecto.supply_core.security.context.PlataformaAutenticada;

/** Grupos en la consola de la plataforma: crear, asignar empresas y crear al super administrador (spec 035). */
class GrupoPlataformaServiceTest {

    private final GrupoEmpresaRepository grupos = mock(GrupoEmpresaRepository.class);
    private final UsuarioGrupoRepository superAdmins = mock(UsuarioGrupoRepository.class);
    private final UsuarioGrupoEmpresaRepository vinculos = mock(UsuarioGrupoEmpresaRepository.class);
    private final EmpresaRepository empresas = mock(EmpresaRepository.class);
    private final GrupoVinculoService vinculo = mock(GrupoVinculoService.class);
    private final PlataformaAuditoriaService auditoria = mock(PlataformaAuditoriaService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final TrasladoGrupoRepository traslados = mock(TrasladoGrupoRepository.class);
    private final GrupoPlataformaService service = new GrupoPlataformaService(grupos, superAdmins, vinculos, empresas, traslados,
            vinculo, encoder, auditoria);

    private GrupoEmpresa grupo;
    private Empresa a;
    private Empresa b;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(PlataformaActual.ATRIBUTO, new PlataformaAutenticada(3L, "dueno@x.com", "Dueño"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        grupo = new GrupoEmpresa();
        grupo.setId(2L);
        grupo.setNombre("Ferrecentro");
        a = empresa(10L, null);
        b = empresa(11L, null);
        when(grupos.findById(2L)).thenReturn(Optional.of(grupo));
        when(grupos.save(any())).thenAnswer(i -> {
            GrupoEmpresa g = i.getArgument(0);
            g.setId(9L);
            return g;
        });
        when(empresas.findById(10L)).thenReturn(Optional.of(a));
        when(empresas.findById(11L)).thenReturn(Optional.of(b));
        when(superAdmins.saveAndFlush(any())).thenAnswer(i -> {
            UsuarioGrupo u = i.getArgument(0);
            u.setId(70L);
            return u;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static Empresa empresa(Long id, Long grupoId) {
        Empresa e = new Empresa();
        e.setId(id);
        e.setRazonSocial("Empresa " + id);
        e.setCodigoPublico("emp" + id);
        e.setGrupoId(grupoId);
        return e;
    }

    private static UsuarioGrupo superAdmin(Long id, String correo, boolean activo) {
        UsuarioGrupo u = new UsuarioGrupo();
        u.setId(id);
        u.setGrupoId(2L);
        u.setNombre("Super " + id);
        u.setCorreo(correo);
        u.setActivo(activo);
        return u;
    }

    @Test
    void crearUnGrupoLimpiaElNombreLoAudita() {
        var r = service.crear("  Ferre   Norte ");

        assertEquals("Ferre Norte", r.nombre());
        assertTrue(r.empresas().isEmpty());
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.GRUPO_CREAR), isNull(), eq(true), anyString());
    }

    @Test
    void noSePuedeCrearUnGrupoConNombreRepetido() {
        when(grupos.existsByNombreIgnoreCase("Ferrecentro")).thenReturn(true);

        var ex = assertThrows(NegocioException.class, () -> service.crear("Ferrecentro"));

        assertEquals("GRUPO_DUPLICADO", ex.getCodigo());
        verify(grupos, never()).save(any());
    }

    @Test
    void alAsignarUnaEmpresaVinculaACadaSuperAdministradorActivo() {
        when(superAdmins.findByGrupoIdOrderByIdAsc(2L)).thenReturn(List.of(superAdmin(70L, "s1@g.com", true),
                superAdmin(71L, "s2@g.com", false)));

        var r = service.asignarEmpresa(10L, 2L);

        assertEquals(2L, r.grupoId());
        assertEquals(2L, a.getGrupoId());
        verify(vinculo).vincular(eq(70L), eq(10L), eq("Super 70"), eq("s1@g.com"));
        verify(vinculo, never()).vincular(eq(71L), anyLong(), anyString(), anyString());
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.GRUPO_ASIGNAR_EMPRESA), eq(10L), eq(true), anyString());
    }

    @Test
    void siUnCorreoChocaConUnUsuarioDeLaEmpresaNoSeCambiaNada() {
        when(superAdmins.findByGrupoIdOrderByIdAsc(2L)).thenReturn(List.of(superAdmin(70L, "s1@g.com", true)));
        doThrow(new NegocioException("CORREO_EN_USO_EMPRESA", "choca")).when(vinculo).exigirCorreoLibre(70L, 10L,
                "s1@g.com");

        assertThrows(NegocioException.class, () -> service.asignarEmpresa(10L, 2L));

        assertNull(a.getGrupoId());
        verify(empresas, never()).save(any());
        verify(vinculo, never()).vincular(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void alSacarUnaEmpresaDelGrupoSeDesactivanLosUsuariosVinculados() {
        a.setGrupoId(2L);
        when(superAdmins.findByGrupoIdOrderByIdAsc(2L)).thenReturn(List.of(superAdmin(70L, "s1@g.com", true),
                superAdmin(71L, "s2@g.com", false)));

        var r = service.asignarEmpresa(10L, null);

        assertNull(r.grupoId());
        assertNull(a.getGrupoId());
        verify(vinculo).desvincular(70L, 10L);
        verify(vinculo).desvincular(71L, 10L);
        verify(vinculo, never()).vincular(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void alCambiarDeGrupoSeDesvinculaDelAnteriorYSeVinculaAlNuevo() {
        a.setGrupoId(5L);
        GrupoEmpresa otro = new GrupoEmpresa();
        otro.setId(6L);
        otro.setNombre("Otro");
        when(grupos.findById(6L)).thenReturn(Optional.of(otro));
        UsuarioGrupo viejo = superAdmin(70L, "viejo@g.com", true);
        viejo.setGrupoId(5L);
        UsuarioGrupo nuevo = superAdmin(80L, "nuevo@g.com", true);
        nuevo.setGrupoId(6L);
        when(superAdmins.findByGrupoIdOrderByIdAsc(5L)).thenReturn(List.of(viejo));
        when(superAdmins.findByGrupoIdOrderByIdAsc(6L)).thenReturn(List.of(nuevo));

        service.asignarEmpresa(10L, 6L);

        verify(vinculo).desvincular(70L, 10L);
        verify(vinculo).vincular(eq(80L), eq(10L), anyString(), eq("nuevo@g.com"));
        assertEquals(6L, a.getGrupoId());
    }

    @Test
    void unaEmpresaOUnGrupoInexistentesDan404() {
        assertThrows(NoEncontradoException.class, () -> service.asignarEmpresa(99L, 2L));
        assertThrows(NoEncontradoException.class, () -> service.asignarEmpresa(10L, 99L));
        assertThrows(NoEncontradoException.class,
                () -> service.crearSuperAdmin(99L, new SuperAdminCrearRequest("Ana", "ana@g.com", "ClaveSegura1234")));
    }

    @Test
    void crearAlSuperAdministradorGuardaLaClaveConHashYLoVinculaEnCadaEmpresaDelGrupo() {
        when(empresas.findByGrupoIdOrderByIdAsc(2L)).thenReturn(List.of(empresa(10L, 2L), empresa(11L, 2L)));

        var r = service.crearSuperAdmin(2L, new SuperAdminCrearRequest(" Ana Pérez ", " ANA@Grupo.com ", "ClaveSegura1234"));

        ArgumentCaptor<UsuarioGrupo> captura = ArgumentCaptor.forClass(UsuarioGrupo.class);
        verify(superAdmins).saveAndFlush(captura.capture());
        UsuarioGrupo guardado = captura.getValue();
        assertEquals("ana@grupo.com", guardado.getCorreo());
        assertEquals("Ana Pérez", guardado.getNombre());
        assertNotEquals("ClaveSegura1234", guardado.getHashPassword());
        assertTrue(encoder.matches("ClaveSegura1234", guardado.getHashPassword()));
        assertFalse(guardado.isMfaActivo());
        assertEquals(70L, r.id());
        verify(vinculo).vincular(70L, 10L, "Ana Pérez", "ana@grupo.com");
        verify(vinculo).vincular(70L, 11L, "Ana Pérez", "ana@grupo.com");
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.GRUPO_SUPER_ADMIN_CREAR), isNull(), eq(true), anyString());
    }

    @Test
    void unCorreoYaUsadoPorOtroSuperAdministradorSeRechaza() {
        when(superAdmins.existsByCorreoIgnoreCase("ana@grupo.com")).thenReturn(true);

        var ex = assertThrows(NegocioException.class,
                () -> service.crearSuperAdmin(2L, new SuperAdminCrearRequest("Ana", "ana@grupo.com", "ClaveSegura1234")));

        assertEquals("CORREO_DUPLICADO", ex.getCodigo());
        verify(superAdmins, never()).saveAndFlush(any());
    }

    @Test
    void siFallaElVinculoDeUnaEmpresaElSuperAdministradorQuedaDesactivadoYSinVinculos() {
        when(empresas.findByGrupoIdOrderByIdAsc(2L)).thenReturn(List.of(empresa(10L, 2L), empresa(11L, 2L)));
        doThrow(new IllegalStateException("falla")).when(vinculo).vincular(eq(70L), eq(11L), anyString(), anyString());
        when(vinculos.findByUsuarioGrupoId(70L)).thenReturn(List.of(new UsuarioGrupoEmpresa(70L, 10L, 500L)));

        assertThrows(IllegalStateException.class,
                () -> service.crearSuperAdmin(2L, new SuperAdminCrearRequest("Ana", "ana@grupo.com", "ClaveSegura1234")));

        ArgumentCaptor<UsuarioGrupo> captura = ArgumentCaptor.forClass(UsuarioGrupo.class);
        verify(superAdmins, org.mockito.Mockito.atLeastOnce()).save(captura.capture());
        assertFalse(captura.getValue().isActivo());
        assertEquals(1, captura.getValue().getVersionSesion());
        verify(vinculo).desvincular(70L, 10L);
        verify(vinculo).desvincular(70L, 11L);
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.GRUPO_SUPER_ADMIN_CREAR), isNull(), eq(false), anyString());
    }

    @Test
    void desactivarAlSuperAdministradorSubeSuVersionDeSesionYDesactivaSusUsuariosEnTodasLasEmpresasVinculadas() {
        UsuarioGrupo u = superAdmin(70L, "ana@grupo.com", true);
        u.setVersionSesion(2);
        when(superAdmins.findById(70L)).thenReturn(Optional.of(u));
        when(empresas.findByGrupoIdOrderByIdAsc(2L)).thenReturn(List.of(empresa(10L, 2L)));
        // vínculo con una empresa que ya salió del grupo: también se corta
        when(vinculos.findByUsuarioGrupoId(70L)).thenReturn(List.of(new UsuarioGrupoEmpresa(70L, 10L, 500L),
                new UsuarioGrupoEmpresa(70L, 12L, 501L)));

        var r = service.cambiarEstado(2L, 70L, false);

        assertFalse(r.activo());
        assertEquals(3, u.getVersionSesion());
        verify(vinculo).desvincular(70L, 10L);
        verify(vinculo).desvincular(70L, 12L);
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.GRUPO_SUPER_ADMIN_ESTADO), isNull(), eq(true), anyString());
    }

    @Test
    void reactivarAlSuperAdministradorLoVuelveAVincularEnLasEmpresasDelGrupo() {
        UsuarioGrupo u = superAdmin(70L, "ana@grupo.com", false);
        when(superAdmins.findById(70L)).thenReturn(Optional.of(u));
        when(empresas.findByGrupoIdOrderByIdAsc(2L)).thenReturn(List.of(empresa(10L, 2L)));

        var r = service.cambiarEstado(2L, 70L, true);

        assertTrue(r.activo());
        verify(vinculo).vincular(70L, 10L, "Super 70", "ana@grupo.com");
    }

    @Test
    void unSuperAdministradorDeOtroGrupoNoSePuedeCambiarDesdeEste() {
        UsuarioGrupo u = superAdmin(70L, "ana@grupo.com", true);
        u.setGrupoId(5L);
        when(superAdmins.findById(70L)).thenReturn(Optional.of(u));

        assertThrows(NoEncontradoException.class, () -> service.cambiarEstado(2L, 70L, false));
        assertTrue(u.isActivo());
    }

    @Test
    void conTrasladosEnTransitoNoSeSacaUnaEmpresaDelGrupoNiSePasaAOtro() {
        a.setGrupoId(2L);
        GrupoEmpresa otro = new GrupoEmpresa();
        otro.setId(6L);
        otro.setNombre("Otro");
        when(grupos.findById(6L)).thenReturn(Optional.of(otro));
        when(traslados.hayEnTransito(10L)).thenReturn(true);

        NegocioException sacar = assertThrows(NegocioException.class, () -> service.asignarEmpresa(10L, null));
        NegocioException pasar = assertThrows(NegocioException.class, () -> service.asignarEmpresa(10L, 6L));

        assertEquals("TRASLADOS_EN_TRANSITO", sacar.getCodigo());
        assertEquals("TRASLADOS_EN_TRANSITO", pasar.getCodigo());
        assertEquals(2L, a.getGrupoId());
        verify(vinculo, never()).desvincular(anyLong(), anyLong());
        verify(empresas, never()).save(any());
    }

    @Test
    void conTrasladosEnTransitoSiSePuedeRepetirLaAsignacionAlMismoGrupo() {
        a.setGrupoId(2L);
        when(traslados.hayEnTransito(10L)).thenReturn(true);

        var r = service.asignarEmpresa(10L, 2L);

        assertEquals(2L, r.grupoId());
    }

    @Test
    void reiniciarElMfaBorraElSecretoCortaLasSesionesYLoAudita() {
        UsuarioGrupo u = superAdmin(70L, "ana@grupo.com", true);
        u.setMfaSecreto("SECRETO");
        u.setMfaActivo(true);
        u.setMfaUltimoPaso(55L);
        u.setVersionSesion(4);
        when(superAdmins.findById(70L)).thenReturn(Optional.of(u));

        var r = service.reiniciarMfa(2L, 70L);

        assertNull(u.getMfaSecreto());
        assertFalse(u.isMfaActivo());
        assertNull(u.getMfaUltimoPaso());
        assertEquals(5, u.getVersionSesion());
        assertFalse(r.mfaActivo());
        verify(superAdmins).save(u);
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.GRUPO_SUPER_ADMIN_REINICIAR_MFA), isNull(), eq(true),
                anyString());
    }

    @Test
    void noSeReiniciaElMfaDeUnSuperAdministradorDeOtroGrupo() {
        UsuarioGrupo u = superAdmin(70L, "ana@grupo.com", true);
        u.setGrupoId(5L);
        u.setMfaActivo(true);
        when(superAdmins.findById(70L)).thenReturn(Optional.of(u));

        assertThrows(NoEncontradoException.class, () -> service.reiniciarMfa(2L, 70L));
        assertTrue(u.isMfaActivo());
        verify(auditoria, never()).registrar(anyLong(), eq(AccionPlataforma.GRUPO_SUPER_ADMIN_REINICIAR_MFA), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), anyString());
    }
}
