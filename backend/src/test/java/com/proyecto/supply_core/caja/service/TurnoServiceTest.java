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
import com.proyecto.supply_core.caja.entity.CajaUsuario;
import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;
import com.proyecto.supply_core.caja.enums.TipoDenominacion;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.caja.repository.CajaUsuarioRepository;
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
    @Mock private CajaUsuarioRepository cajaUsuarioRepository;
    @Mock private com.proyecto.supply_core.venta.repository.VentaRepository ventaRepository;

    private TurnoService servicio;

    /** Arma el servicio y deja un cajero en la sesión. */
    @BeforeEach
    void crear() {
        servicio = new TurnoService(turnoRepository, cajaRepository, movimientoRepository, empresaRepository,
                usuarioRepository, autorizacionService, notificacionService, auditoriaService, categoriaService,
                categoriaRepository, cajaUsuarioRepository, ventaRepository);
        sesion("CAJERO");
    }

    /** @param rol rol de la persona 7 en la sesión */
    private static void sesion(String rol) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(7L, "persona@x.com", "Persona", Set.of(rol), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    // ------------------------------------------------------------------ caja con permisos (spec 025)

    /** Quien tiene cajas asignadas no abre turno en otra. */
    @Test
    void conCajasAsignadasNoAbreEnUnaQueNoEsSuya() {
        Caja caja = new Caja();
        caja.setId(3L);
        caja.setNombre("Caja 1");
        caja.setActiva(true);
        when(cajaRepository.conDetalle(3L)).thenReturn(Optional.of(caja));
        CajaUsuario otra = new CajaUsuario();
        otra.setCajaId(4L);
        otra.setUsuarioId(7L);
        when(cajaUsuarioRepository.findByUsuarioId(7L)).thenReturn(List.of(otra));

        var e = catchThrowableOfType(com.proyecto.supply_core.common.exception.AccesoDenegadoException.class,
                () -> servicio.abrir(new AbrirTurnoRequest(3L, BigDecimal.ZERO)));

        assertThat(e).isNotNull();
        assertThat(e.getCodigo()).isEqualTo("CAJA_NO_ASIGNADA");
        verify(turnoRepository, never()).saveAndFlush(any());
    }

    /** Sin cajas asignadas puede abrir en cualquiera (regla de transición). */
    @Test
    void sinCajasAsignadasAbreEnCualquiera() {
        cajaLibre();
        when(cajaUsuarioRepository.findByUsuarioId(7L)).thenReturn(List.of());

        servicio.abrir(new AbrirTurnoRequest(3L, BigDecimal.ZERO));

        verify(turnoRepository).saveAndFlush(any());
    }

    /** Deja el turno 5 abierto, con su caja y efectivo de sobra para retirar. */
    private void turnoConEfectivo() {
        Caja caja = new Caja();
        caja.setId(3L);
        caja.setNombre("Caja 1");
        caja.setTopeEfectivo(BigDecimal.ZERO);
        TurnoCaja t = new TurnoCaja();
        t.setId(5L);
        t.setCaja(caja);
        t.setCajeroUsuarioId(7L);
        t.setEstado(EstadoTurno.ABIERTO);
        when(turnoRepository.bloquear(5L)).thenReturn(Optional.of(t));
        when(movimientoRepository.saldo(5L, MedioPago.EFECTIVO)).thenReturn(new BigDecimal("100000"));
    }

    /** D15: con el permiso, el retiro no pide PIN y queda sin quién autorizó. */
    @Test
    void conPermisoElRetiroNoPidePin() {
        sesion("SUPERVISOR_VENTAS");
        turnoConEfectivo();

        servicio.registrarMovimiento(5L, new MovimientoCajaRequest(TipoMovimientoCaja.RETIRO, new BigDecimal("20000"),
                "A la bóveda", null, null, null));

        verify(autorizacionService, never()).consumir(any(), any(), any(), any());
        var guardado = org.mockito.ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoRepository).save(guardado.capture());
        assertThat(guardado.getValue().getAutorizadoPorUsuarioId()).isNull();
        assertThat(guardado.getValue().getMonto()).isEqualByComparingTo("-20000");
    }

    /** D15: sin el permiso, el retiro sigue pidiendo la autorización del supervisor. */
    @Test
    void sinPermisoElRetiroSiguePidiendoPin() {
        turnoConEfectivo();
        when(autorizacionService.consumir(any(), any(), any(), any()))
                .thenThrow(new NegocioException("AUTORIZACION_REQUERIDA", "Esta acción necesita la autorización"));

        NegocioException e = catchThrowableOfType(NegocioException.class, () -> servicio.registrarMovimiento(5L,
                new MovimientoCajaRequest(TipoMovimientoCaja.RETIRO, new BigDecimal("20000"), "A la bóveda", null,
                        null, null)));

        assertThat(e.getCodigo()).isEqualTo("AUTORIZACION_REQUERIDA");
        verify(movimientoRepository, never()).save(any());
    }

    /** Cada tipo tiene su permiso para hacerlo sin PIN. */
    @Test
    void cadaMovimientoTieneSuPermisoSinPin() {
        assertThat(TurnoService.permisoSinPin(TipoMovimientoCaja.RETIRO)).isEqualTo("CAJA_RETIRO_SIN_PIN");
        assertThat(TurnoService.permisoSinPin(TipoMovimientoCaja.GASTO)).isEqualTo("CAJA_GASTO_SIN_PIN");
        assertThat(TurnoService.permisoSinPin(TipoMovimientoCaja.INGRESO)).isEqualTo("CAJA_INGRESO_SIN_PIN");
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

    /** Los movimientos de una venta traen su id, consecutivo y total; un retiro no trae nada de venta. */
    @Test
    void losMovimientosDeUnaVentaTraenConsecutivoYTotal() {
        sesion("SUPERVISOR_VENTAS");
        Caja caja = new Caja();
        caja.setId(3L);
        caja.setNombre("Caja 1");
        TurnoCaja t = new TurnoCaja();
        t.setId(5L);
        t.setCaja(caja);
        t.setCajeroUsuarioId(7L);
        t.setEstado(EstadoTurno.ABIERTO);
        when(turnoRepository.conCaja(5L)).thenReturn(Optional.of(t));
        MovimientoCaja v1 = movimientoDeVenta(TipoMovimientoCaja.VENTA, MedioPago.EFECTIVO, "1565000", "VENTA", 9L);
        MovimientoCaja v2 = movimientoDeVenta(TipoMovimientoCaja.VENTA, MedioPago.TARJETA_CREDITO, "1000000", "VENTA", 9L);
        MovimientoCaja retiro = movimientoDeVenta(TipoMovimientoCaja.RETIRO, MedioPago.EFECTIVO, "-20000", null, null);
        when(movimientoRepository.findByTurnoIdOrderByIdAsc(5L)).thenReturn(List.of(v1, v2, retiro));
        com.proyecto.supply_core.venta.entity.Venta venta = new com.proyecto.supply_core.venta.entity.Venta();
        venta.setId(9L);
        venta.setConsecutivo("C2-000001");
        venta.setTotal(new BigDecimal("2565000"));
        when(ventaRepository.findAllById(List.of(9L))).thenReturn(List.of(venta));
        try {
            TurnoResponse r = servicio.obtener(5L);

            assertThat(r.movimientos()).hasSize(3);
            assertThat(r.movimientos().get(0).consecutivo()).isEqualTo("C2-000001");
            assertThat(r.movimientos().get(0).referenciaId()).isEqualTo(9L);
            assertThat(r.movimientos().get(1).totalVenta()).isEqualByComparingTo("2565000");
            assertThat(r.movimientos().get(2).consecutivo()).isNull();
            assertThat(r.movimientos().get(2).totalVenta()).isNull();
        } finally {
            EmpresaContexto.limpiar();
        }
    }

    /**
     * @param tipo     tipo
     * @param medio    medio
     * @param monto    monto con signo
     * @param refTipo  tipo de referencia o {@code null}
     * @param refId    id de la referencia o {@code null}
     * @return movimiento del turno 5
     */
    private static MovimientoCaja movimientoDeVenta(TipoMovimientoCaja tipo, MedioPago medio, String monto,
            String refTipo, Long refId) {
        MovimientoCaja m = new MovimientoCaja();
        m.setTurnoId(5L);
        m.setTipo(tipo);
        m.setMedioPago(medio);
        m.setMonto(new BigDecimal(monto));
        m.setReferenciaTipo(refTipo);
        m.setReferenciaId(refId);
        return m;
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
