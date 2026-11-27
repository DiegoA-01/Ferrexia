package com.proyecto.supply_core.grupo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.AsignacionGrupoResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.EmpresaEntrableResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.GrupoResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.SesionGrupoResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.SuperAdminResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.TableroEmpresaResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.TableroGrupoResponse;
import com.proyecto.supply_core.grupo.service.GrupoAuthService;
import com.proyecto.supply_core.grupo.service.GrupoEmpresaService;
import com.proyecto.supply_core.grupo.service.GrupoPlataformaService;
import com.proyecto.supply_core.grupo.service.GrupoTableroService;
import com.proyecto.supply_core.plataforma.dto.DesafioMfaResponse;
import com.proyecto.supply_core.security.dto.LoginResponse;

/** Rutas de los grupos: quién entra a cada una y cómo se validan los datos (spec 035). */
@WebMvcTest({ GrupoController.class, GrupoAuthController.class, PlataformaGrupoController.class })
class GrupoControllerTest extends WebTestBase {

    @MockitoBean
    private GrupoEmpresaService empresaService;

    @MockitoBean
    private GrupoTableroService tableroService;

    @MockitoBean
    private GrupoAuthService authService;

    @MockitoBean
    private GrupoPlataformaService plataformaService;

    private static final String SUPER_ADMIN = """
            {"nombre":"Ana Pérez","correo":"ana@grupo.com","password":"ClaveSegura1234"}""";

    @Test
    void laZonaDelGrupoSoloAceptaElTokenDelGrupo() throws Exception {
        mvc.perform(get("/api/grupo/empresas")).andExpect(status().isUnauthorized());
        // ni el de una empresa (aunque sea del Dueño), ni el de la plataforma, ni el de un proveedor
        mvc.perform(get("/api/grupo/empresas").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/grupo/tablero").header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/grupo/entrar/10").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/grupo/entrar/10").header(HttpHeaders.AUTHORIZATION, bearerProveedor(4L)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(empresaService, tableroService);
    }

    @Test
    void elTokenDelGrupoNoAbreLaConsolaDeLaPlataformaNiLaApiDeUnaEmpresa() throws Exception {
        mvc.perform(get("/api/plataforma/grupos").header(HttpHeaders.AUTHORIZATION, bearerGrupo()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/plataforma/grupos").header(HttpHeaders.AUTHORIZATION, bearerGrupo())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, bearerGrupo()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(plataformaService);
    }

    @Test
    void laConsolaDeGruposSoloAceptaElTokenDeLaPlataforma() throws Exception {
        mvc.perform(get("/api/plataforma/grupos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/plataforma/grupos").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/plataforma/empresas/10/grupo").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grupoId\":2}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(plataformaService);
    }

    @Test
    void elIngresoDelGrupoEsPublicoYDevuelveElDesafio() throws Exception {
        when(authService.login(any())).thenReturn(new DesafioMfaResponse("desafio", false, "SECRETO", "otpauth://totp/x"));
        when(authService.verificarMfa(any())).thenReturn(
                new SesionGrupoResponse("jwt", "Bearer", 900, 7L, "ana@grupo.com", "Ana", 2L, "Ferrecentro"));

        mvc.perform(post("/api/grupo/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@grupo.com\",\"password\":\"x\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.desafio").value("desafio"));
        mvc.perform(post("/api/grupo/auth/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafio\":\"d\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grupoId").value(2));
    }

    @Test
    void elIngresoDelGrupoValidaLosDatos() throws Exception {
        mvc.perform(post("/api/grupo/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/grupo/auth/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafio\":\"d\",\"codigo\":\"12\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    @Test
    void unCodigoOClaveMalosDan401() throws Exception {
        when(authService.login(any())).thenThrow(new NoAutenticadoException("Credenciales inválidas"));

        mvc.perform(post("/api/grupo/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@grupo.com\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conElTokenDelGrupoSeVenLasEmpresasSeEntraYSeLeeElTablero() throws Exception {
        when(empresaService.empresas()).thenReturn(List.of(new EmpresaEntrableResponse(10L, "A", "ACTIVA")));
        when(empresaService.entrar(10L)).thenReturn(new LoginResponse("jwt", "Bearer", 900, null, "refresh"));
        when(empresaService.entrar(99L)).thenThrow(new NoEncontradoException("La empresa indicada no existe"));
        TableroEmpresaResponse total = new TableroEmpresaResponse(null, null, new BigDecimal("10.00"),
                new BigDecimal("5.00"), new BigDecimal("1.00"));
        when(tableroService.tablero(any(), any())).thenReturn(
                new TableroGrupoResponse(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 27), List.of(), total));

        mvc.perform(get("/api/grupo/empresas").header(HttpHeaders.AUTHORIZATION, bearerGrupo()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(10));
        mvc.perform(post("/api/grupo/entrar/10").header(HttpHeaders.AUTHORIZATION, bearerGrupo()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
        mvc.perform(post("/api/grupo/entrar/99").header(HttpHeaders.AUTHORIZATION, bearerGrupo()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/grupo/entrar/0").header(HttpHeaders.AUTHORIZATION, bearerGrupo()))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/grupo/tablero?desde=2026-11-01&hasta=2026-11-27").header(HttpHeaders.AUTHORIZATION, bearerGrupo()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total.ventasNetas").value(10.00));
        verify(tableroService).tablero(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 27));
    }

    @Test
    void laPlataformaCreaGruposAsignaEmpresasYCreaAlSuperAdministrador() throws Exception {
        when(plataformaService.listar()).thenReturn(List.of());
        when(plataformaService.crear("Ferrecentro")).thenReturn(new GrupoResponse(2L, "Ferrecentro", true, List.of(), List.of()));
        when(plataformaService.asignarEmpresa(10L, 2L)).thenReturn(new AsignacionGrupoResponse(10L, 2L));
        when(plataformaService.asignarEmpresa(10L, null)).thenReturn(new AsignacionGrupoResponse(10L, null));
        when(plataformaService.crearSuperAdmin(eq(2L), any()))
                .thenReturn(new SuperAdminResponse(70L, "Ana Pérez", "ana@grupo.com", true, false));
        when(plataformaService.cambiarEstado(2L, 70L, false))
                .thenReturn(new SuperAdminResponse(70L, "Ana Pérez", "ana@grupo.com", false, false));

        mvc.perform(get("/api/plataforma/grupos").header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/plataforma/grupos").header(HttpHeaders.AUTHORIZATION, bearerPlataforma())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Ferrecentro\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(2));
        mvc.perform(put("/api/plataforma/empresas/10/grupo").header(HttpHeaders.AUTHORIZATION, bearerPlataforma())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grupoId\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grupoId").value(2));
        mvc.perform(put("/api/plataforma/empresas/10/grupo").header(HttpHeaders.AUTHORIZATION, bearerPlataforma())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grupoId\":null}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/plataforma/grupos/2/super-admin").header(HttpHeaders.AUTHORIZATION, bearerPlataforma())
                        .contentType(MediaType.APPLICATION_JSON).content(SUPER_ADMIN))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.correo").value("ana@grupo.com"))
                .andExpect(jsonPath("$.hashPassword").doesNotExist());
        mvc.perform(patch("/api/plataforma/grupos/2/super-admin/70").header(HttpHeaders.AUTHORIZATION, bearerPlataforma())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        verify(plataformaService).asignarEmpresa(eq(10L), isNull());
    }

    @Test
    void laClaveDelSuperAdministradorDebeSerFuerteYLosDatosValidos() throws Exception {
        String corta = "{\"nombre\":\"Ana\",\"correo\":\"ana@grupo.com\",\"password\":\"Corta123\"}";
        String sinNumero = "{\"nombre\":\"Ana\",\"correo\":\"ana@grupo.com\",\"password\":\"SoloLetrasLargas\"}";
        String correoMalo = "{\"nombre\":\"Ana\",\"correo\":\"no-es-correo\",\"password\":\"ClaveSegura1234\"}";

        for (String cuerpo : List.of(corta, sinNumero, correoMalo, "{}")) {
            mvc.perform(post("/api/plataforma/grupos/2/super-admin").header(HttpHeaders.AUTHORIZATION, bearerPlataforma())
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/plataforma/grupos").header(HttpHeaders.AUTHORIZATION, bearerPlataforma())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/plataforma/grupos/0/super-admin").header(HttpHeaders.AUTHORIZATION, bearerPlataforma())
                        .contentType(MediaType.APPLICATION_JSON).content(SUPER_ADMIN))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(plataformaService);
    }

    @Test
    void laPlataformaReiniciaElMfaDeUnSuperAdministradorYSoloConSuToken() throws Exception {
        when(plataformaService.reiniciarMfa(2L, 70L))
                .thenReturn(new SuperAdminResponse(70L, "Ana Pérez", "ana@grupo.com", true, false));

        mvc.perform(post("/api/plataforma/grupos/2/super-admin/70/reiniciar-mfa")
                        .header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mfaActivo").value(false));
        mvc.perform(post("/api/plataforma/grupos/2/super-admin/70/reiniciar-mfa")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/plataforma/grupos/2/super-admin/70/reiniciar-mfa")
                        .header(HttpHeaders.AUTHORIZATION, bearerGrupo()))
                .andExpect(status().isUnauthorized());
        verify(plataformaService).reiniciarMfa(2L, 70L);
    }
}
