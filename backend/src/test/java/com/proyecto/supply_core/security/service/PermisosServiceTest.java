package com.proyecto.supply_core.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.ResultSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/** Permisos efectivos (spec 022): los del rol, más los sumados, menos los quitados; recordados unos segundos. */
class PermisosServiceTest {

    private JdbcTemplate jdbc;
    private PermisosService servicio;
    private final UsuarioAutenticado cajero = new UsuarioAutenticado(7L, "c@x.com", "Cajero", Set.of("CAJERO"), null, 1L);

    /**
     * Simula las dos consultas: el cajero cobra y ve ventas por su rol; a mano se le suma ver la auditoría y se le quita
     * ver ventas.
     */
    @BeforeEach
    void preparar() {
        jdbc = mock(JdbcTemplate.class);
        servicio = new PermisosService(jdbc, 60);
        responder("FROM rol_permiso", List.of(new Object[] { "CAJERO", "VENTA_COBRAR" }, new Object[] { "CAJERO", "VENTA_VER" },
                new Object[] { "AUDITOR", "AUDITORIA_VER" }));
        doAnswer(i -> {
            RowCallbackHandler h = i.getArgument(1);
            fila(h, "AUDITORIA_VER", true);
            fila(h, "VENTA_VER", false);
            return null;
        }).when(jdbc).query(Mockito.contains("FROM usuario_permiso"), any(RowCallbackHandler.class), eq(7L), eq(1L));
    }

    /**
     * @param sql   parte de la consulta
     * @param filas pares rol-permiso
     */
    private void responder(String sql, List<Object[]> filas) {
        doAnswer(i -> {
            RowCallbackHandler h = i.getArgument(1);
            for (Object[] f : filas) {
                ResultSet rs = mock(ResultSet.class);
                Mockito.when(rs.getString(1)).thenReturn((String) f[0]);
                Mockito.when(rs.getString(2)).thenReturn((String) f[1]);
                h.processRow(rs);
            }
            return null;
        }).when(jdbc).query(Mockito.contains(sql), any(RowCallbackHandler.class));
    }

    /**
     * @param h         manejador de filas
     * @param permiso   permiso
     * @param concedido sumado o quitado
     * @throws Exception nunca
     */
    private static void fila(RowCallbackHandler h, String permiso, boolean concedido) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Mockito.when(rs.getString(1)).thenReturn(permiso);
        Mockito.when(rs.getBoolean(2)).thenReturn(concedido);
        h.processRow(rs);
    }

    @Test
    void sumaLosDelRolYLosSumadosYRestaLosQuitados() {
        assertThat(servicio.tiene(cajero, "VENTA_COBRAR")).isTrue();      // del rol
        assertThat(servicio.tiene(cajero, "AUDITORIA_VER")).isTrue();     // sumado a mano
        assertThat(servicio.tiene(cajero, "VENTA_VER")).isFalse();        // quitado a mano
        assertThat(servicio.tiene(cajero, "USUARIO_GESTIONAR")).isFalse();
        assertThat(servicio.efectivos(cajero)).containsExactlyInAnyOrder("VENTA_COBRAR", "AUDITORIA_VER");
    }

    @Test
    void recuerdaLosPermisosHastaQueSeLimpian() {
        servicio.tiene(cajero, "VENTA_COBRAR");
        servicio.tiene(cajero, "VENTA_VER");
        verify(jdbc, times(1)).query(Mockito.contains("FROM rol_permiso"), any(RowCallbackHandler.class));

        servicio.limpiar();
        servicio.tiene(cajero, "VENTA_COBRAR");
        verify(jdbc, times(2)).query(Mockito.contains("FROM rol_permiso"), any(RowCallbackHandler.class));
    }

    @Test
    void losPermisosSueltosSeBuscanSoloEnLaEmpresaDeLaSesion() {
        servicio.tiene(cajero, "VENTA_COBRAR");
        verify(jdbc).query(Mockito.contains("up.empresa_id = ?"), any(RowCallbackHandler.class), eq(7L), eq(1L));
        verify(jdbc, Mockito.never()).query(anyString(), any(RowCallbackHandler.class), eq(7L), eq(2L));
    }
}
