package com.proyecto.supply_core.caja.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.dto.AbrirTurnoRequest;
import com.proyecto.supply_core.caja.entity.Caja;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.caja.repository.MovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/** Apertura de turno cuando dos personas abren la misma caja a la vez. */
@ExtendWith(MockitoExtension.class)
class TurnoServiceTest {

    @Mock private TurnoCajaRepository turnoRepository;
    @Mock private CajaRepository cajaRepository;
    @Mock private MovimientoCajaRepository movimientoRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AutorizacionService autorizacionService;
    @Mock private NotificacionService notificacionService;
    @Mock private AuditoriaService auditoriaService;

    private TurnoService servicio;

    /** Arma el servicio y deja un cajero en la sesión. */
    @BeforeEach
    void crear() {
        servicio = new TurnoService(turnoRepository, cajaRepository, movimientoRepository, empresaRepository,
                usuarioRepository, autorizacionService, notificacionService, auditoriaService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(7L, "cajero@x.com", "Cajero", Set.of("CAJERO"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    /** Limpia la sesión del hilo. */
    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** Deja una caja activa y libre, como la ven los dos cajeros antes de guardar. */
    private void cajaLibre() {
        Caja caja = new Caja();
        caja.setId(3L);
        caja.setNombre("Caja 1");
        caja.setActiva(true);
        when(cajaRepository.conDetalle(3L)).thenReturn(Optional.of(caja));
        when(turnoRepository.abiertoDe(7L)).thenReturn(Optional.empty());
        when(turnoRepository.existsByCajaIdAndEstado(3L, EstadoTurno.ABIERTO)).thenReturn(false);
    }

    /** Si MySQL resuelve la carrera con un interbloqueo, la respuesta es 409 TURNO_ABIERTO y no un error interno. */
    @Test
    void elInterbloqueoAlAbrirLaMismaCajaSeRespondeComoTurnoAbierto() {
        cajaLibre();
        when(turnoRepository.saveAndFlush(any())).thenThrow(new CannotAcquireLockException("Deadlock found"));

        NegocioException e = catchThrowableOfType(NegocioException.class,
                () -> servicio.abrir(new AbrirTurnoRequest(3L, new BigDecimal("1000"))));

        assertThat(e).isNotNull();
        assertThat(e.getCodigo()).isEqualTo("TURNO_ABIERTO");
    }

    /** La llave duplicada (el otro caso de la misma carrera) sigue respondiendo igual. */
    @Test
    void laLlaveDuplicadaAlAbrirSeRespondeComoTurnoAbierto() {
        cajaLibre();
        when(turnoRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        NegocioException e = catchThrowableOfType(NegocioException.class,
                () -> servicio.abrir(new AbrirTurnoRequest(3L, new BigDecimal("1000"))));

        assertThat(e).isNotNull();
        assertThat(e.getCodigo()).isEqualTo("TURNO_ABIERTO");
    }
}
