package com.proyecto.supply_core.grupo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupoEmpresa;
import com.proyecto.supply_core.grupo.repository.UsuarioGrupoEmpresaRepository;
import com.proyecto.supply_core.plataforma.entity.Suscripcion;
import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;
import com.proyecto.supply_core.security.context.GrupoActual;
import com.proyecto.supply_core.security.context.GrupoAutenticado;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.service.AuthService;

/** Empresas del grupo y entrada a cada una (spec 035). */
class GrupoEmpresaServiceTest {

    private final EmpresaRepository empresas = mock(EmpresaRepository.class);
    private final UsuarioGrupoEmpresaRepository vinculos = mock(UsuarioGrupoEmpresaRepository.class);
    private final SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final GrupoEmpresaService service = new GrupoEmpresaService(empresas, vinculos, suscripciones, auth);

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GrupoActual.ATRIBUTO, new GrupoAutenticado(7L, 2L, "super@grupo.com", "Super"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
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

    private static Suscripcion suscripcion(EstadoSuscripcion estado) {
        Suscripcion s = new Suscripcion();
        s.setEstado(estado);
        return s;
    }

    @Test
    void entrarEmiteLaSesionNormalDeLaEmpresaConElUsuarioVinculadoDentroDeSuContexto() {
        when(empresas.findById(10L)).thenReturn(Optional.of(empresa(10L, 2L)));
        when(vinculos.findByUsuarioGrupoIdAndEmpresaId(7L, 10L)).thenReturn(Optional.of(new UsuarioGrupoEmpresa(7L, 10L, 500L)));
        LoginResponse sesion = new LoginResponse("jwt", "Bearer", 900, null, "refresh");
        when(auth.iniciarSesionDeGrupo(500L, 7L)).thenAnswer(i -> {
            assertEquals(10L, EmpresaContexto.requerida()); // corre dentro de la empresa
            return sesion;
        });

        assertEquals(sesion, service.entrar(10L));
    }

    @Test
    void unaEmpresaDeOtroGrupoOSinVinculoOInexistenteDanElMismo404() {
        when(empresas.findById(20L)).thenReturn(Optional.of(empresa(20L, 5L))); // otro grupo
        when(empresas.findById(10L)).thenReturn(Optional.of(empresa(10L, 2L))); // del grupo, sin vínculo
        when(vinculos.findByUsuarioGrupoIdAndEmpresaId(7L, 10L)).thenReturn(Optional.empty());
        when(empresas.findById(30L)).thenReturn(Optional.of(empresa(30L, null))); // sin grupo

        var otroGrupo = assertThrows(NoEncontradoException.class, () -> service.entrar(20L));
        var sinVinculo = assertThrows(NoEncontradoException.class, () -> service.entrar(10L));
        var inexistente = assertThrows(NoEncontradoException.class, () -> service.entrar(99L));
        assertThrows(NoEncontradoException.class, () -> service.entrar(30L));

        assertEquals(otroGrupo.getMessage(), sinVinculo.getMessage());
        assertEquals(otroGrupo.getMessage(), inexistente.getMessage());
        verify(auth, never()).iniciarSesionDeGrupo(anyLong(), anyLong());
    }

    @Test
    void laListaTraeSoloLasEmpresasDelGrupoConVinculoYSuscripcionQueDejaEntrar() {
        when(empresas.findByGrupoIdOrderByIdAsc(2L)).thenReturn(List.of(empresa(10L, 2L), empresa(11L, 2L),
                empresa(12L, 2L), empresa(13L, 2L)));
        when(vinculos.findByUsuarioGrupoIdAndEmpresaId(7L, 10L)).thenReturn(Optional.of(new UsuarioGrupoEmpresa(7L, 10L, 500L)));
        when(vinculos.findByUsuarioGrupoIdAndEmpresaId(7L, 11L)).thenReturn(Optional.of(new UsuarioGrupoEmpresa(7L, 11L, 501L)));
        when(vinculos.findByUsuarioGrupoIdAndEmpresaId(7L, 12L)).thenReturn(Optional.empty()); // sin vínculo
        when(vinculos.findByUsuarioGrupoIdAndEmpresaId(7L, 13L)).thenReturn(Optional.of(new UsuarioGrupoEmpresa(7L, 13L, 503L)));
        when(suscripciones.findByEmpresaId(10L)).thenReturn(Optional.of(suscripcion(EstadoSuscripcion.ACTIVA)));
        when(suscripciones.findByEmpresaId(11L)).thenReturn(Optional.of(suscripcion(EstadoSuscripcion.SUSPENDIDA)));
        when(suscripciones.findByEmpresaId(13L)).thenReturn(Optional.of(suscripcion(EstadoSuscripcion.MOROSA)));

        var r = service.empresas();

        assertEquals(List.of(10L, 13L), r.stream().map(e -> e.id()).toList());
        assertEquals("ACTIVA", r.get(0).estado());
    }
}
