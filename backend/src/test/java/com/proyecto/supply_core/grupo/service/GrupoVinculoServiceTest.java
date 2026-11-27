package com.proyecto.supply_core.grupo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupoEmpresa;
import com.proyecto.supply_core.grupo.repository.UsuarioGrupoEmpresaRepository;
import com.proyecto.supply_core.security.service.SesionUsuarioService;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/** Usuario Dueño vinculado al super administrador dentro de una empresa (spec 035). */
class GrupoVinculoServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final UsuarioGrupoEmpresaRepository vinculos = mock(UsuarioGrupoEmpresaRepository.class);
    private final SesionUsuarioService sesiones = mock(SesionUsuarioService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final GrupoVinculoService service = new GrupoVinculoService(usuarios, roles, encoder, vinculos, sesiones);

    @BeforeEach
    void setUp() {
        Rol dueno = new Rol();
        dueno.setCodigo("ADMIN_SISTEMA");
        when(roles.findByCodigo("ADMIN_SISTEMA")).thenReturn(Optional.of(dueno));
        when(usuarios.saveAndFlush(any())).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setId(500L);
            return u;
        });
    }

    @Test
    void vincularCreaUnDuenoConClaveAleatoriaQueNadieConoceYGuardaElVinculo() {
        Long id = service.vincular(70L, 10L, " Ana Pérez ", "Ana@Grupo.com");

        assertEquals(500L, id);
        ArgumentCaptor<Usuario> captura = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(captura.capture());
        Usuario u = captura.getValue();
        assertEquals("ana@grupo.com", u.getCorreo());
        assertEquals("Ana Pérez", u.getNombreCompleto());
        assertTrue(u.codigosRoles().contains("ADMIN_SISTEMA"));
        assertTrue(u.isActivo());
        assertFalse(encoder.matches("", u.getHashPassword()));
        assertFalse(encoder.matches("ana@grupo.com", u.getHashPassword()));
        verify(vinculos).save(any(UsuarioGrupoEmpresa.class));
    }

    @Test
    void cadaVinculoTieneUnaClaveAleatoriaDistinta() {
        service.vincular(70L, 10L, "Ana", "ana@grupo.com");
        service.vincular(70L, 11L, "Ana", "ana@grupo.com");

        ArgumentCaptor<Usuario> captura = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios, org.mockito.Mockito.times(2)).saveAndFlush(captura.capture());
        assertNotEquals(captura.getAllValues().get(0).getHashPassword(), captura.getAllValues().get(1).getHashPassword());
    }

    @Test
    void siElCorreoYaLoUsaOtroUsuarioDeLaEmpresaNoSeCreaNada() {
        when(usuarios.existsByCorreoIgnoreCase("ana@grupo.com")).thenReturn(true);

        var ex = assertThrows(NegocioException.class, () -> service.vincular(70L, 10L, "Ana", "ana@grupo.com"));

        assertEquals("CORREO_EN_USO_EMPRESA", ex.getCodigo());
        verify(usuarios, never()).saveAndFlush(any());
        verify(vinculos, never()).save(any());
    }

    @Test
    void vincularDeNuevoReactivaAlMismoUsuarioSinCrearOtro() {
        Usuario existente = new Usuario();
        existente.setId(500L);
        existente.setActivo(false);
        existente.setIntentosFallidos(4);
        when(vinculos.findByUsuarioGrupoIdAndEmpresaId(70L, 10L))
                .thenReturn(Optional.of(new UsuarioGrupoEmpresa(70L, 10L, 500L)));
        when(usuarios.findWithRolesById(500L)).thenReturn(Optional.of(existente));

        Long id = service.vincular(70L, 10L, "Ana", "ana@grupo.com");

        assertEquals(500L, id);
        assertTrue(existente.isActivo());
        assertEquals(0, existente.getIntentosFallidos());
        assertTrue(existente.codigosRoles().contains("ADMIN_SISTEMA"));
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void desvincularDesactivaAlUsuarioYCortaSusSesiones() {
        Usuario existente = new Usuario();
        existente.setId(500L);
        when(vinculos.findByUsuarioGrupoIdAndEmpresaId(70L, 10L))
                .thenReturn(Optional.of(new UsuarioGrupoEmpresa(70L, 10L, 500L)));
        when(usuarios.findWithRolesById(500L)).thenReturn(Optional.of(existente));

        service.desvincular(70L, 10L);

        assertFalse(existente.isActivo());
        verify(sesiones).revocarSesiones(existente);
    }

    @Test
    void desvincularSinVinculoNoHaceNada() {
        service.desvincular(70L, 10L);

        verify(sesiones, never()).revocarSesiones(any());
    }
}
