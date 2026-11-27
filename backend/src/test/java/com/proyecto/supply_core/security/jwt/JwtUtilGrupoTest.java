package com.proyecto.supply_core.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.security.context.GrupoAutenticado;
import com.proyecto.supply_core.security.context.PlataformaAutenticada;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/** Los tokens del grupo, los de una empresa, los de la plataforma y los desafíos de MFA no se pueden confundir (spec 035). */
class JwtUtilGrupoTest {

    private final JwtUtil jwt = new JwtUtil(new JwtProperties("clave-de-prueba-de-al-menos-32-caracteres!!", 15));

    private final GrupoAutenticado superAdmin = new GrupoAutenticado(7L, 2L, "super@grupo.com", "Super Admin");

    @Test
    void elTokenDelGrupoVaYVuelveConSuVersionDeSesion() {
        String token = jwt.generarGrupo(superAdmin, 4);

        assertEquals(superAdmin, jwt.validarGrupo(token));
        assertEquals(4, jwt.versionSesion(token));
    }

    @Test
    void elTokenDelGrupoNoSirveEnLaApiDeUnaEmpresaNiEnLaPlataforma() {
        String token = jwt.generarGrupo(superAdmin, 0);

        assertThrows(NoAutenticadoException.class, () -> jwt.validar(token));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarPlataforma(token));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfa(token));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfaEmpresa(token));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfaGrupo(token));
    }

    @Test
    void ElTokenDeEmpresaYElDeLaPlataformaNoSirvenEnLaZonaDelGrupo() {
        String deEmpresa = jwt.generar(new UsuarioAutenticado(5L, "ana@demo.com", "Ana", Set.of("ADMIN_SISTEMA"), null, 1L));
        String dePlataforma = jwt.generarPlataforma(new PlataformaAutenticada(3L, "dueno@supplycore.local", "Dueño"));

        assertThrows(NoAutenticadoException.class, () -> jwt.validarGrupo(deEmpresa));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarGrupo(dePlataforma));
    }

    @Test
    void elDesafioDeMfaDelGrupoNoEsUnaSesionNiSirveEnOtroLado() {
        String desafio = jwt.generarDesafioMfaGrupo(7L);

        assertEquals(7L, jwt.validarDesafioMfaGrupo(desafio));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarGrupo(desafio));
        assertThrows(NoAutenticadoException.class, () -> jwt.validar(desafio));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarPlataforma(desafio));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfa(desafio));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfaEmpresa(desafio));
    }

    @Test
    void elDesafioDeLaPlataformaYElDeUnaEmpresaNoSirvenComoDesafioDelGrupo() {
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfaGrupo(jwt.generarDesafioMfa(3L)));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfaGrupo(jwt.generarDesafioMfaEmpresa(3L, 1L)));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfaGrupo(jwt.generarGrupo(superAdmin, 0)));
    }

    @Test
    void unTokenAlteradoOBasuraSeRechaza() {
        String token = jwt.generarGrupo(superAdmin, 0);

        assertThrows(NoAutenticadoException.class, () -> jwt.validarGrupo(token + "x"));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarGrupo("basura"));
        assertThrows(NoAutenticadoException.class, () -> jwt.validarDesafioMfaGrupo("basura"));
    }
}
