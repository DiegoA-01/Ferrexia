package com.proyecto.supply_core.caja.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
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
import com.proyecto.supply_core.caja.dto.CerrarTurnoRequest;
import com.proyecto.supply_core.caja.dto.ConteoDenominacion;
import com.proyecto.supply_core.caja.dto.MovimientoCajaRequest;
import com.proyecto.supply_core.caja.dto.TurnoResponse;
import com.proyecto.supply_core.caja.entity.Caja;
import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;
import com.proyecto.supply_core.caja.enums.TipoDenominacion;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.caja.repository.CategoriaMovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.MovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.entity.Empresa;
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

    // ------------------------------------------------------------------ cierre por denominación (spec 023)

    /**
     * Deja el turno 5 abierto en la Caja 1 con base de 50.000 en efectivo, y la empresa con su tolerancia.
     *
     * @return el turno
     */
    private TurnoCaja turnoConBase() {
        Caja caja = new Caja();
        caja.setId(3L);
        caja.setNombre("Caja 1");
        TurnoCaja t = new TurnoCaja();
        t.setId(5L);
        t.setCaja(caja);
        t.setCajeroUsuarioId(7L);
        t.setEstado(EstadoTurno.ABIERTO);
        when(turnoRepository.bloquear(5L)).thenReturn(Optional.of(t));
        MovimientoCaja base = new MovimientoCaja();
        base.setTurnoId(5L);
        base.setTipo(TipoMovimientoCaja.BASE);
        base.setMedioPago(MedioPago.EFECTIVO);
        base.setMonto(new BigDecimal("50000.00"));
        when(movimientoRepository.findByTurnoIdOrderByIdAsc(5L)).thenReturn(List.of(base));
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(new Empresa()));
        EmpresaContexto.fijar(1L);
        return t;
    }

    /**
     * @param tipo     billete o moneda
     * @param valor    valor
     * @param cantidad unidades
     * @return fila del conteo
     */
    private static ConteoDenominacion fila(TipoDenominacion tipo, int valor, int cantidad) {
        return new ConteoDenominacion(tipo, valor, cantidad, null);
    }

    /** Con el conteo, Java suma el efectivo, lo usa como contado y guarda el conteo sin las filas en cero. */
    @Test
    void cerrarConConteoGuardaElConteoYElContadoCalculado() {
        TurnoCaja t = turnoConBase();
        try {
            TurnoResponse r = servicio.cerrar(5L, new CerrarTurnoRequest(null, null, null,
                    List.of(fila(TipoDenominacion.BILLETE, 20_000, 2), fila(TipoDenominacion.MONEDA, 1_000, 10),
                            fila(TipoDenominacion.BILLETE, 100_000, 0))));

            assertThat(t.getContadoEfectivo()).isEqualByComparingTo("50000");
            assertThat(r.diferencia()).isEqualByComparingTo("0");
            assertThat(r.conteo()).hasSize(2);
            assertThat(r.conteo()).extracting(ConteoDenominacion::valor).containsExactly(20_000, 1_000);
            assertThat(t.getConteoDenominaciones()).contains("20000").doesNotContain("100000");
        } finally {
            EmpresaContexto.limpiar();
        }
    }

    /** Si el cajero escribe el total y además cuenta, los dos deben coincidir. */
    @Test
    void elConteoQueNoCuadraConElTotalEscritoSeRechaza() {
        turnoAbierto();
        NegocioException e = catchThrowableOfType(NegocioException.class,
                () -> servicio.cerrar(5L, new CerrarTurnoRequest(new BigDecimal("60000"), null, null,
                        List.of(fila(TipoDenominacion.BILLETE, 50_000, 1)))));

        assertThat(e).isNotNull();
        assertThat(e.getCodigo()).isEqualTo("CONTEO_NO_CUADRA");
        verify(turnoRepository, never()).saveAndFlush(any());
    }

    /** Un billete inventado no cierra el turno. */
    @Test
    void unaDenominacionInventadaNoCierraElTurno() {
        turnoAbierto();
        NegocioException e = catchThrowableOfType(NegocioException.class,
                () -> servicio.cerrar(5L, new CerrarTurnoRequest(null, null, null,
                        List.of(fila(TipoDenominacion.BILLETE, 25_000, 1)))));

        assertThat(e).isNotNull();
        assertThat(e.getCodigo()).isEqualTo("DENOMINACION_INVALIDA");
        verify(turnoRepository, never()).saveAndFlush(any());
    }

    /** Sin conteo ni total no hay nada que cerrar. */
    @Test
    void sinConteoNiTotalSeRechaza() {
        turnoAbierto();
        NegocioException e = catchThrowableOfType(NegocioException.class,
                () -> servicio.cerrar(5L, new CerrarTurnoRequest(null, null, null)));

        assertThat(e).isNotNull();
        assertThat(e.getCodigo()).isEqualTo("CONTADO_OBLIGATORIO");
    }

    /** Sin conteo el cierre funciona como antes: vale el total escrito y no se guarda conteo. */
    @Test
    void sinConteoFuncionaComoAntes() {
        TurnoCaja t = turnoConBase();
        try {
            TurnoResponse r = servicio.cerrar(5L, new CerrarTurnoRequest(new BigDecimal("49000"), null, "faltó"));

            assertThat(t.getContadoEfectivo()).isEqualByComparingTo("49000");
            assertThat(r.diferencia()).isEqualByComparingTo("-1000");
            assertThat(r.conteo()).isNull();
            assertThat(t.getConteoDenominaciones()).isNull();
        } finally {
            EmpresaContexto.limpiar();
        }
    }
}
