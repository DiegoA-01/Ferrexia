package com.proyecto.supply_core.caja.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.proyecto.supply_core.caja.dto.MovimientoCajaRequest;
import com.proyecto.supply_core.caja.entity.Caja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.caja.repository.CategoriaMovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.MovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/** Apertura de turno cuando dos personas abren la misma caja a la vez, y categorías de ingresos y egresos (spec 021). */
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
    @Mock private CategoriaMovimientoCajaService categoriaService;
    @Mock private CategoriaMovimientoCajaRepository categoriaRepository;

    private TurnoService servicio;

    /** Arma el servicio y deja un cajero en la sesión. */
    @BeforeEach
    void crear() {
        servicio = new TurnoService(turnoRepository, cajaRepository, movimientoRepository, empresaRepository,
                usuarioRepository, autorizacionService, notificacionService, auditoriaService, categoriaService,
                categoriaRepository);
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

    /** Deja el turno 5 abierto y del cajero de la sesión. */
    private void turnoAbierto() {
        TurnoCaja t = new TurnoCaja();
        t.setId(5L);
        t.setCajeroUsuarioId(7L);
        t.setEstado(EstadoTurno.ABIERTO);
        when(turnoRepository.bloquear(5L)).thenReturn(Optional.of(t));
    }

    /**
     * @param tipo        tipo
     * @param categoriaId categoría
     * @return movimiento de 5.000 con una autorización cualquiera
     */
    private static MovimientoCajaRequest movimiento(TipoMovimientoCaja tipo, Long categoriaId) {
        return new MovimientoCajaRequest(tipo, new BigDecimal("5000"), "Resma", null, "permiso", categoriaId);
    }

    /** El gasto y el ingreso sin categoría se rechazan antes de gastar la autorización del supervisor. */
    @Test
    void elGastoYElIngresoSinCategoriaSeRechazanSinGastarElPermiso() {
        turnoAbierto();
        for (TipoMovimientoCaja tipo : new TipoMovimientoCaja[] { TipoMovimientoCaja.GASTO, TipoMovimientoCaja.INGRESO }) {
            NegocioException e = catchThrowableOfType(NegocioException.class,
                    () -> servicio.registrarMovimiento(5L, movimiento(tipo, null)));
            assertThat(e).isNotNull();
            assertThat(e.getCodigo()).isEqualTo("CATEGORIA_OBLIGATORIA");
        }
        verify(autorizacionService, never()).consumir(any(), any(), any(), any());
    }

    /** El gasto pide una categoría de egreso y el ingreso una de ingreso: la que no sirve se rechaza con 409. */
    @Test
    void laCategoriaDebeSerDelTipoDelMovimiento() {
        turnoAbierto();
        when(categoriaService.paraMovimiento(9L, TipoCategoriaMovimiento.EGRESO))
                .thenThrow(new NegocioException("CATEGORIA_INVALIDA", "Elija una categoría de egreso activa"));

        NegocioException e = catchThrowableOfType(NegocioException.class,
                () -> servicio.registrarMovimiento(5L, movimiento(TipoMovimientoCaja.GASTO, 9L)));

        assertThat(e).isNotNull();
        assertThat(e.getCodigo()).isEqualTo("CATEGORIA_INVALIDA");
        verify(autorizacionService, never()).consumir(any(), any(), any(), any());
    }
}
