package com.proyecto.supply_core.venta.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.venta.dto.CambiarPagoRequest;
import com.proyecto.supply_core.venta.dto.PagoVentaRequest;
import com.proyecto.supply_core.venta.dto.VentaResponse;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.entity.VentaPago;
import com.proyecto.supply_core.venta.entity.VentaPagoCambio;
import com.proyecto.supply_core.venta.enums.EstadoVenta;
import com.proyecto.supply_core.venta.enums.MedioPagoVenta;
import com.proyecto.supply_core.venta.repository.DevolucionVentaRepository;
import com.proyecto.supply_core.venta.repository.VentaPagoCambioRepository;
import com.proyecto.supply_core.venta.repository.VentaRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Cambiar el método de pago de una venta (spec 029, D16): el cajero marcó efectivo y era tarjeta, por ejemplo. El total
 * no cambia: los pagos nuevos suman exactamente lo mismo. La diferencia por medio queda en la caja como
 * {@code AJUSTE_PAGO} en el turno de la venta si sigue abierto, o en el turno abierto de quien cambia. Los pagos de la
 * venta quedan con los vigentes y el antes y el después en {@code venta_pago_cambio} y en la auditoría. El crédito y el
 * saldo a favor no se tocan aquí: son cartera.
 */
@Service
public class CambioPagoVentaService {

    private final VentaRepository ventaRepository;
    private final TurnoCajaRepository turnoRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final DevolucionVentaRepository devolucionRepository;
    private final VentaPagoCambioRepository cambioRepository;
    private final AutorizacionService autorizacionService;
    private final AuditoriaService auditoriaService;
    private final VentaService ventaService;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * @param ventaRepository          ventas
     * @param turnoRepository          turnos
     * @param movimientoCajaRepository libro de caja
     * @param devolucionRepository     devoluciones (una venta con devoluciones no cambia su pago)
     * @param cambioRepository         historial de cambios de pago
     * @param autorizacionService      PIN del supervisor
     * @param auditoriaService         registro del cambio
     * @param ventaService             detalle de la venta
     */
    public CambioPagoVentaService(VentaRepository ventaRepository, TurnoCajaRepository turnoRepository,
            MovimientoCajaRepository movimientoCajaRepository, DevolucionVentaRepository devolucionRepository,
            VentaPagoCambioRepository cambioRepository, AutorizacionService autorizacionService,
            AuditoriaService auditoriaService, VentaService ventaService) {
        this.ventaRepository = ventaRepository;
        this.turnoRepository = turnoRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.devolucionRepository = devolucionRepository;
        this.cambioRepository = cambioRepository;
        this.autorizacionService = autorizacionService;
        this.auditoriaService = auditoriaService;
        this.ventaService = ventaService;
    }

    /**
     * @param id  venta
     * @param req pagos nuevos, motivo y, sin el permiso, la autorización del supervisor
     * @return la venta con sus pagos vigentes y su historial
     * @throws NoEncontradoException   si la venta no es de la empresa
     * @throws AccesoDenegadoException si la sesión no tiene el permiso y no es el cajero del turno abierto de la venta
     * @throws NegocioException        si la venta está anulada o tiene devoluciones, hay crédito o saldo a favor, los
     *                                 pagos no suman el total o no cambian nada, no hay turno abierto donde dejar el
     *                                 ajuste, falta efectivo o la autorización no sirve
     */
    @Transactional
    public VentaResponse cambiar(Long id, CambiarPagoRequest req) {
        Venta v = ventaRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
        if (v.getEstado() != EstadoVenta.CONFIRMADA) {
            throw new NegocioException("VENTA_ANULADA", "La venta " + v.getConsecutivo() + " está anulada");
        }
        if (devolucionRepository.existsByVentaId(v.getId())) {
            throw new NegocioException("TIENE_DEVOLUCIONES", "La venta tiene devoluciones: su pago ya no se cambia");
        }
        if (v.getPagos().stream().anyMatch(p -> !p.getMedio().entraACaja())
                || req.pagos().stream().anyMatch(p -> !p.medio().entraACaja())) {
            throw new NegocioException("PAGO_DE_CARTERA",
                    "El crédito y el saldo a favor no se cambian aquí: se manejan en la cartera del cliente");
        }
        Map<MedioPago, BigDecimal> antes = porMedio(v.getPagos().stream().map(p -> Map.entry(p.getMedio(), p.getMonto()))
                .toList());
        Map<MedioPago, BigDecimal> despues = porMedio(req.pagos().stream()
                .map(p -> Map.entry(p.medio(), CalculoVenta.dinero(p.monto()))).toList());
        BigDecimal total = suma(despues);
        if (total.compareTo(v.totalAPagar()) != 0) {
            throw new NegocioException("PAGOS_NO_CUADRAN", "Los pagos nuevos suman " + total.toPlainString()
                    + " y la venta es de " + v.totalAPagar().toPlainString() + ": deben sumar lo mismo");
        }
        if (iguales(antes, despues)) {
            throw new NegocioException("SIN_CAMBIOS", "Los pagos nuevos son iguales a los de la venta");
        }

        Long yo = SesionActual.usuario().id();
        boolean conPermiso = SesionActual.usuario().tienePermiso(Permisos.VENTA_CAMBIAR_PAGO);
        TurnoCaja original = turnoRepository.bloquear(v.getTurnoId())
                .orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        boolean originalAbierto = original.getEstado() == EstadoTurno.ABIERTO;
        if (!conPermiso && !(originalAbierto && original.getCajeroUsuarioId().equals(yo))) {
            throw new AccesoDenegadoException(
                    "Solo el cajero de un turno abierto cambia el pago de sus ventas, con el PIN del supervisor");
        }
        TurnoCaja turno = originalAbierto ? original : turnoRepository.abiertoDeBloqueado(yo)
                .orElseThrow(() -> new NegocioException("SIN_TURNO_ABIERTO",
                        "Abra un turno: el ajuste de la venta de un turno cerrado queda en su caja"));

        Map<MedioPago, BigDecimal> diferencias = new LinkedHashMap<>();
        for (MedioPago m : MedioPago.values()) {
            BigDecimal d = despues.getOrDefault(m, BigDecimal.ZERO).subtract(antes.getOrDefault(m, BigDecimal.ZERO));
            if (d.signum() != 0) {
                diferencias.put(m, d);
            }
        }
        BigDecimal saleEfectivo = diferencias.getOrDefault(MedioPago.EFECTIVO, BigDecimal.ZERO).negate();
        if (saleEfectivo.signum() > 0
                && movimientoCajaRepository.saldo(turno.getId(), MedioPago.EFECTIVO).compareTo(saleEfectivo) < 0) {
            throw new NegocioException("EFECTIVO_INSUFICIENTE",
                    "No hay efectivo suficiente en la caja para sacar " + saleEfectivo.toPlainString());
        }
        String motivo = req.motivo().trim();
        Long supervisor = conPermiso ? null
                : autorizacionService.consumir(req.autorizacion(), AccionAutorizada.CAMBIAR_PAGO, turno.getId(), v.getId());

        // Caja: lo que sale del medio viejo y entra al nuevo
        diferencias.forEach((medio, monto) -> {
            MovimientoCaja m = new MovimientoCaja();
            m.setTurnoId(turno.getId());
            m.setTipo(TipoMovimientoCaja.AJUSTE_PAGO);
            m.setMedioPago(medio);
            m.setMonto(monto);
            m.setReferenciaTipo(VentaService.REFERENCIA);
            m.setReferenciaId(v.getId());
            m.setMotivo("Pago corregido de " + v.getConsecutivo() + ": " + motivo);
            m.setUsuarioId(yo);
            m.setAutorizadoPorUsuarioId(supervisor);
            movimientoCajaRepository.save(m);
        });

        // Pagos vigentes de la venta
        v.getPagos().clear();
        ventaRepository.saveAndFlush(v);
        for (PagoVentaRequest p : req.pagos()) {
            VentaPago x = new VentaPago();
            x.setVenta(v);
            x.setMedio(p.medio());
            x.setMonto(CalculoVenta.dinero(p.monto()));
            x.setRecibido(p.medio() == MedioPagoVenta.EFECTIVO ? x.getMonto() : null);
            x.setReferencia(p.referencia() == null || p.referencia().isBlank() ? null : p.referencia().trim());
            x.setUltimos4(p.ultimos4());
            v.getPagos().add(x);
        }

        VentaPagoCambio c = new VentaPagoCambio();
        c.setVentaId(v.getId());
        c.setTurnoId(turno.getId());
        c.setUsuarioId(yo);
        c.setAutorizadoPorUsuarioId(supervisor);
        c.setAntes(json.writeValueAsString(texto(antes)));
        c.setDespues(json.writeValueAsString(texto(despues)));
        c.setMotivo(motivo);
        cambioRepository.save(c);
        auditoriaService.exito(AccionAuditoria.VENTA_CAMBIAR_PAGO, EntidadAuditada.VENTA, v.getId(), v.getConsecutivo()
                + "; antes=" + texto(antes) + "; despues=" + texto(despues) + "; turnoAjuste=" + turno.getId()
                + "; autorizó=" + supervisor + "; motivo=" + motivo);
        return ventaService.respuesta(v);
    }

    /**
     * @param pagos medio y monto de cada pago
     * @return monto por medio de caja, en el orden de los medios
     */
    private static Map<MedioPago, BigDecimal> porMedio(List<Map.Entry<MedioPagoVenta, BigDecimal>> pagos) {
        Map<MedioPago, BigDecimal> mapa = new LinkedHashMap<>();
        for (MedioPago m : MedioPago.values()) {
            for (Map.Entry<MedioPagoVenta, BigDecimal> p : pagos) {
                if (p.getKey().enCaja() == m) {
                    mapa.merge(m, p.getValue(), BigDecimal::add);
                }
            }
        }
        return mapa;
    }

    /**
     * @param mapa monto por medio
     * @return suma
     */
    private static BigDecimal suma(Map<MedioPago, BigDecimal> mapa) {
        return mapa.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * @param a montos por medio
     * @param b montos por medio
     * @return {@code true} si dejan lo mismo en cada medio
     */
    private static boolean iguales(Map<MedioPago, BigDecimal> a, Map<MedioPago, BigDecimal> b) {
        List<MedioPago> medios = new ArrayList<>(a.keySet());
        b.keySet().stream().filter(m -> !medios.contains(m)).forEach(medios::add);
        return medios.stream()
                .allMatch(m -> a.getOrDefault(m, BigDecimal.ZERO).compareTo(b.getOrDefault(m, BigDecimal.ZERO)) == 0);
    }

    /**
     * @param mapa monto por medio
     * @return el mismo mapa con los medios como texto (para el JSON y la auditoría)
     */
    private static Map<String, BigDecimal> texto(Map<MedioPago, BigDecimal> mapa) {
        Map<String, BigDecimal> t = new LinkedHashMap<>();
        mapa.forEach((m, v) -> t.put(m.name(), v));
        return t;
    }
}
