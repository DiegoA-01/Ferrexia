package com.proyecto.supply_core.grupo.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.grupo.entity.GrupoEmpresa;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupo;
import com.proyecto.supply_core.grupo.repository.GrupoEmpresaRepository;
import com.proyecto.supply_core.grupo.repository.UsuarioGrupoRepository;
import com.proyecto.supply_core.security.context.GrupoAutenticado;

/** Un token de grupo deja de valer si cambia la versión, se desactiva la cuenta o el grupo (spec 035). */
class GrupoSesionServiceTest {

    private final UsuarioGrupoRepository usuarios = mock(UsuarioGrupoRepository.class);
    private final GrupoEmpresaRepository grupos = mock(GrupoEmpresaRepository.class);
    private final GrupoSesionService service = new GrupoSesionService(usuarios, grupos);
    private final GrupoAutenticado token = new GrupoAutenticado(7L, 2L, "super@grupo.com", "Super");
    private UsuarioGrupo u;
    private GrupoEmpresa g;

    @BeforeEach
    void setUp() {
        u = new UsuarioGrupo();
        u.setId(7L);
        u.setGrupoId(2L);
        u.setVersionSesion(3);
        g = new GrupoEmpresa();
        g.setId(2L);
        when(usuarios.findById(7L)).thenReturn(Optional.of(u));
        when(grupos.findById(2L)).thenReturn(Optional.of(g));
    }

    @Test
    void conLaMismaVersionYTodoActivoSigueValiendo() {
        assertDoesNotThrow(() -> service.exigirVigente(token, 3));
    }

    @Test
    void conOtraVersionLaCuentaInactivaElGrupoInactivoOElUsuarioInexistenteYaNoVale() {
        assertThrows(NoAutenticadoException.class, () -> service.exigirVigente(token, 2));
        u.setActivo(false);
        assertThrows(NoAutenticadoException.class, () -> service.exigirVigente(token, 3));
        u.setActivo(true);
        g.setActivo(false);
        assertThrows(NoAutenticadoException.class, () -> service.exigirVigente(token, 3));
        g.setActivo(true);
        assertThrows(NoAutenticadoException.class,
                () -> service.exigirVigente(new GrupoAutenticado(99L, 2L, "x", "x"), 3));
    }

    @Test
    void siElUsuarioCambioDeGrupoElTokenViejoNoVale() {
        assertThrows(NoAutenticadoException.class,
                () -> service.exigirVigente(new GrupoAutenticado(7L, 5L, "super@grupo.com", "Super"), 3));
    }
}
