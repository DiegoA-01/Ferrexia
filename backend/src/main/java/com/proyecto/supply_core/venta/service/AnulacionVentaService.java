package com.proyecto.supply_core.venta.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.AccionAutorizada;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;
import com.proyecto.supply_core.caja.repository.MovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.caja.service.AutorizacionService;
import com.proyecto.supply_core.cartera.service.CarteraService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.venta.dto.AnularOtroTurnoRequest;
import com.proyecto.supply_core.venta.dto.AnularVentaRequest;
import com.proyecto.supply_core.venta.dto.VentaResponse;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.enums.EstadoVenta;
import com.proyecto.supply_core.venta.repository.DevolucionVentaRepository;
import com.proyecto.supply_core.venta.repository.VentaRepository;

/**
 * Anular una venta (spec 006): del turno abierto, por su cajero y con la autorización de otra persona. Devuelve el
 * stock ({@code ANULACION_VENTA}) y registra en la caja la salida del dinero por cada medio ({@code ANULACION}, en
 * negativo). La venta no se borra: queda {@code ANULADA} con motivo y autorizador, y su consecutivo no se reutiliza.
 * Desde la spec 029, quien tiene el permiso anula también una venta de un turno cerrado: el dinero sale de su propio
 * turno abierto y el turno cerrado no cambia. Lo que se revierte es el neto por medio de la venta (cobro y ajustes de
 * pago), así una venta con el pago corregido se anula con sus pagos vigentes.
 */
@Service
public class AnulacionVentaService {

    private final VentaRepository ventaRepository;
    private final TurnoCajaRepository turnoRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final DevolucionVentaRepository devolucionRepository;
    private final MovimientoInventarioService movimientoInventarioService;
    private final AutorizacionService autorizacionService;
    private final AuditoriaService auditoriaService;
    private final VentaService ventaService;
    private final CarteraService carteraService;
    private final AlertaCajeroService alertaCajero;
    /** Domicilio de la venta: si se anula por fuera del tablero, queda cancelado (spec 032). */
    private com.proyecto.supply_core.domicilio.service.DomicilioRegistro domicilioRegistro;

    /**
     * @param domicilioRegistro domicilios (spec 032)
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDomicilioRegistro(com.proyecto.supply_core.domicilio.service.DomicilioRegistro domicilioRegistro) {
        this.domicilioRegistro = domicilioRegistro;
    }

    /**
     * @param ventaRepository             ventas
     * @param turnoRepository             turno de la venta
     * @param movimientoCajaRepository    libro de caja
     * @param devolucionRepository        devoluciones (una venta con devoluciones no se anula)
     * @param movimientoInventarioService entrada por anulación
     * @param autorizacionService         permiso del supervisor
     * @param auditoriaService            registro de la anulación
     * @param ventaService                detalle de la venta
     * @param carteraService              anula la cuenta y devuelve el saldo a favor (spec 007)
     * @param alertaCajero                alerta de anulaciones por cajero (spec 010)
     */
    public AnulacionVentaService(VentaRepository ventaRepository, TurnoCajaRepository turnoRepository,
            MovimientoCajaRepository movimientoCajaRepository, DevolucionVentaRepository devolucionRepository,
            MovimientoInventarioService movimientoInventarioService, AutorizacionService autorizacionService,
            AuditoriaService auditoriaService, VentaService ventaService, CarteraService carteraService,
            AlertaCajeroService alertaCajero) {
        this.ventaRepository = ventaRepository;
        this.turnoRepository = turnoRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.devolucionRepository = devolucionRepository;
        this.movimientoInventarioService = movimientoInventarioService;
        this.autorizacionService = autorizacionService;
        this.auditoriaService = auditoriaService;
        this.ventaService = ventaService;
        this.carteraService = carteraService;
        this.alertaCajero = alertaCajero;
    }

    /**
     * @param id  venta
     * @param req motivo y autorización
     * @return la venta anulada
     * @throws NoEncontradoException  si la venta no es de la empresa
     * @throws AccesoDenegadoException si quien anula no es el cajero del turno
     * @throws NegocioException        si ya está anulada, su turno está cerrado, tiene devoluciones, la autorización no
     *                                 sirve o no hay efectivo para devolver
     */
    /** Movimientos de caja que forman lo que una venta dejó en la caja: el cobro y sus correcciones de pago. */
    private static final Set<TipoMovimientoCaja> DE_LA_VENTA = Set.of(TipoMovimientoCaja.VENTA,
            TipoMovimientoCaja.AJUSTE_PAGO, TipoMovimientoCaja.COBRO_DOMICILIO);

    @Transactional
    public VentaResponse anular(Long id, AnularVentaRequest req) {
        Venta v = ventaRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
        if (v.getEstado() != EstadoVenta.CONFIRMADA) {
            throw new NegocioException("VENTA_ANULADA", "La venta " + v.getConsecutivo() + " ya está anulada");
        }
        TurnoCaja turno = turnoRepository.bloquear(v.getTurnoId())
                .orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (turno.getEstado() != EstadoTurno.ABIERTO) {
            throw new NegocioException("TURNO_CERRADO",
                    "La venta es de un turno ya cerrado: no se anula, registre una devolución");
        }
        Long yo = SesionActual.usuario().id();
        if (!turno.getCajeroUsuarioId().equals(yo)) {
            throw new AccesoDenegadoException("Solo el cajero del turno anula sus ventas");
        }
        if (devolucionRepository.existsByVentaId(v.getId())) {
            throw new NegocioException("TIENE_DEVOLUCIONES",
                    "La venta ya tiene devoluciones: devuelva el resto en vez de anularla");
        }
        String motivo = req.motivo().trim();
        Map<MedioPago, BigDecimal> netos = netos(v);
        exigirEfectivo(turno, netos);
        Long supervisor = autorizacionService.consumir(req.autorizacion(), AccionAutorizada.ANULAR_VENTA, turno.getId(),
                v.getId());
        aplicar(v, turno, netos, yo, supervisor, motivo, "Anulación de " + v.getConsecutivo());
        auditoriaService.exito(AccionAuditoria.VENTA_ANULAR, EntidadAuditada.VENTA, v.getId(), v.getConsecutivo()
                + "; total=" + v.totalAPagar().toPlainString() + "; autorizó=" + supervisor + "; motivo=" + motivo);
        alertaCajero.revisarDespuesDeConfirmar(v.getCajeroUsuarioId(), AlertaCajeroService.Tipo.ANULACIONES);
        return ventaService.respuesta(v);
    }

    /**
     * Anula una venta de un turno ya cerrado (spec 029). Lo hace quien tiene el permiso, sin PIN: el dinero sale de su
     * turno abierto, el stock vuelve a la bodega de la venta y la cartera se revierte.
     *
     * @param id  venta
     * @param req motivo
     * @return la venta anulada
     * @throws NoEncontradoException si la venta no es de la empresa
     * @throws NegocioException      si ya está anulada, su turno sigue abierto, tiene devoluciones, quien anula no
     *                               tiene turno abierto o en él no hay efectivo para devolver
     */
    @Transactional
    public VentaResponse anularDeOtroTurno(Long id, AnularOtroTurnoRequest req) {
        Venta v = ventaRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
        if (v.getEstado() != EstadoVenta.CONFIRMADA) {
            throw new NegocioException("VENTA_ANULADA", "La venta " + v.getConsecutivo() + " ya está anulada");
        }
        TurnoCaja original = turnoRepository.findById(v.getTurnoId())
                .orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (original.getEstado() == EstadoTurno.ABIERTO) {
            throw new NegocioException("TURNO_ABIERTO",
                    "La venta es de un turno abierto: la anula su cajero desde ese turno, con el PIN del supervisor");
        }
        if (devolucionRepository.existsByVentaId(v.getId())) {
            throw new NegocioException("TIENE_DEVOLUCIONES",
                    "La venta ya tiene devoluciones: devuelva el resto en vez de anularla");
        }
        Long yo = SesionActual.usuario().id();
        TurnoCaja turno = turnoRepository.abiertoDeBloqueado(yo).orElseThrow(() -> new NegocioException(
                "SIN_TURNO_ABIERTO", "Abra un turno: el dinero de la anulación sale de su caja"));
        String motivo = req.motivo().trim();
        Map<MedioPago, BigDecimal> netos = netos(v);
        exigirEfectivo(turno, netos);
        aplicar(v, turno, netos, yo, null, motivo, "Anulación de " + v.getConsecutivo() + " (turno " + original.getId()
                + ")");
        auditoriaService.exito(AccionAuditoria.VENTA_ANULAR, EntidadAuditada.VENTA, v.getId(), v.getConsecutivo()
                + "; total=" + v.totalAPagar().toPlainString() + "; deOtroTurno=" + original.getId() + "; cajaDe="
                + turno.getId() + "; motivo=" + motivo);
        alertaCajero.revisarDespuesDeConfirmar(v.getCajeroUsuarioId(), AlertaCajeroService.Tipo.ANULACIONES);
        return ventaService.respuesta(v);
    }

    /**
     * Anula la venta de un domicilio devuelto o cancelado (spec 032), sin PIN: lo pide quien gestiona domicilios. El
     * stock vuelve; si la venta se pagó antes de salir, el dinero sale del turno abierto de quien la anula.
     *
     * @param ventaId venta del domicilio
     * @param motivo  motivo de la devolución o cancelación
     * @throws NegocioException si ya está anulada, tiene devoluciones, o se pagó y quien anula no tiene turno abierto
     *                          o efectivo suficiente
     */
    @Transactional
    public void anularPorDomicilio(Long ventaId, String motivo) {
        Venta v = ventaRepository.bloquear(ventaId).orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
        if (v.getEstado() != EstadoVenta.CONFIRMADA) {
            throw new NegocioException("VENTA_ANULADA", "La venta " + v.getConsecutivo() + " ya está anulada");
        }
        if (devolucionRepository.existsByVentaId(v.getId())) {
            throw new NegocioException("TIENE_DEVOLUCIONES", "La venta del domicilio ya tiene devoluciones");
        }
        Long yo = SesionActual.usuario().id();
        Map<MedioPago, BigDecimal> netos = netos(v);
        TurnoCaja turno = null;
        if (!netos.isEmpty()) {
            turno = turnoRepository.abiertoDeBloqueado(yo).orElseThrow(() -> new NegocioException("SIN_TURNO_ABIERTO",
                    "El domicilio se pagó al vender: abra un turno, el dinero sale de su caja"));
            exigirEfectivo(turno, netos);
        }
        aplicar(v, turno, netos, yo, null, motivo, "Domicilio devuelto: " + v.getConsecutivo());
        auditoriaService.exito(AccionAuditoria.VENTA_ANULAR, EntidadAuditada.VENTA, v.getId(), v.getConsecutivo()
                + "; total=" + v.totalAPagar().toPlainString() + "; porDomicilio; motivo=" + motivo);
    }

    /**
     * @param v venta
     * @return lo que la venta dejó en la caja por medio (cobro más correcciones de pago), sin los medios en cero
     */
    private Map<MedioPago, BigDecimal> netos(Venta v) {
        Map<MedioPago, BigDecimal> netos = new EnumMap<>(MedioPago.class);
        movimientoCajaRepository.findByReferenciaTipoAndReferenciaId(VentaService.REFERENCIA, v.getId()).stream()
                .filter(m -> DE_LA_VENTA.contains(m.getTipo()))
                .forEach(m -> netos.merge(m.getMedioPago(), m.getMonto(), BigDecimal::add));
        netos.values().removeIf(x -> x.signum() == 0);
        return netos;
    }

    /**
     * @param turno turno del que sale el dinero
     * @param netos lo que sale por medio
     * @throws NegocioException {@code EFECTIVO_INSUFICIENTE} si el turno no tiene el efectivo
     */
    private void exigirEfectivo(TurnoCaja turno, Map<MedioPago, BigDecimal> netos) {
        BigDecimal efectivo = netos.getOrDefault(MedioPago.EFECTIVO, BigDecimal.ZERO);
        if (efectivo.signum() > 0 && movimientoCajaRepository.saldo(turno.getId(), MedioPago.EFECTIVO).compareTo(efectivo) < 0) {
            throw new NegocioException("EFECTIVO_INSUFICIENTE",
                    "No hay efectivo suficiente en la caja para devolver " + efectivo.toPlainString());
        }
    }

    /**
     * Revierte cartera, stock y caja y deja la venta anulada.
     *
     * @param v          venta
     * @param turno      turno del que sale el dinero ({@code null} solo si {@code netos} está vacío)
     * @param netos      lo que sale por medio
     * @param yo         quien anula
     * @param supervisor quien autorizó con su PIN, o {@code null}
     * @param motivo     motivo
     * @param detalle    texto del movimiento de caja
     */
    private void aplicar(Venta v, TurnoCaja turno, Map<MedioPago, BigDecimal> netos, Long yo, Long supervisor,
            String motivo, String detalle) {
        carteraService.revertirVenta(v);

        // Stock: vuelve a la bodega de la caja, por producto en orden de id. Lo que quedó como faltante pendiente
        // (spec 030) nunca salió del stock: el faltante se anula y esa parte no vuelve.
        Map<Long, BigDecimal> pendiente = movimientoInventarioService.anularFaltantes(v.getId());
        Map<Long, BigDecimal> porProducto = new TreeMap<>();
        v.getLineas().forEach(l -> porProducto.merge(l.getProductoId(), l.getCantidadUnidadProducto(), BigDecimal::add));
        porProducto.forEach((productoId, cantidad) -> {
            BigDecimal vuelve = cantidad.subtract(pendiente.getOrDefault(productoId, BigDecimal.ZERO));
            if (vuelve.signum() > 0) {
                movimientoInventarioService.registrarAnulacionVenta(productoId, v.getBodegaId(), vuelve,
                        "Anulación de " + v.getConsecutivo() + ": " + motivo, v.getId());
            }
        });

        netos.forEach((medio, monto) -> {
            MovimientoCaja m = new MovimientoCaja();
            m.setTurnoId(turno.getId());
            m.setTipo(TipoMovimientoCaja.ANULACION);
            m.setMedioPago(medio);
            m.setMonto(monto.negate());
            m.setReferenciaTipo(VentaService.REFERENCIA);
            m.setReferenciaId(v.getId());
            m.setMotivo(detalle);
            m.setUsuarioId(yo);
            m.setAutorizadoPorUsuarioId(supervisor);
            movimientoCajaRepository.save(m);
        });

        v.setEstado(EstadoVenta.ANULADA);
        v.setAnuladaEn(LocalDateTime.now());
        v.setAnuladaPorUsuarioId(yo);
        v.setMotivoAnulacion(motivo);
        v.setAnulacionAutorizadaPorUsuarioId(supervisor);
        if (domicilioRegistro != null) {
            domicilioRegistro.cancelarPorAnulacion(v.getId(), yo, motivo);
        }
    }
}
