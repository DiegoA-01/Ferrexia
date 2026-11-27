package com.proyecto.supply_core.compra.solicitud.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.repository.AdjudicacionRepository;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.repository.CotizacionRepository;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.compra.solicitud.dto.AprobacionResponse;
import com.proyecto.supply_core.compra.solicitud.dto.PasoRecorridoResponse;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.factura.entity.FacturaProveedor;
import com.proyecto.supply_core.factura.repository.FacturaProveedorRepository;
import com.proyecto.supply_core.recepcion.entity.Recepcion;
import com.proyecto.supply_core.recepcion.repository.RecepcionRepository;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Recorrido de una solicitud de compra (spec 033): creación, envío, aprobaciones, RFQ, cotizaciones, adjudicación,
 * órdenes, recepciones y facturas, en orden de fecha. Todas las consultas van por empresa ({@code @TenantId}); quién
 * puede verlo lo decide {@link SolicitudService#obtener(Long)}.
 */
@Service
public class SolicitudRecorridoService {

    private final SolicitudService solicitudService;
    private final RfqRepository rfqRepository;
    private final CotizacionRepository cotizacionRepository;
    private final AdjudicacionRepository adjudicacionRepository;
    private final OrdenCompraRepository ordenRepository;
    private final RecepcionRepository recepcionRepository;
    private final FacturaProveedorRepository facturaRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * @param solicitudService       detalle y control de acceso
     * @param rfqRepository          RFQs de la solicitud
     * @param cotizacionRepository   cotizaciones recibidas
     * @param adjudicacionRepository adjudicaciones
     * @param ordenRepository        órdenes de compra
     * @param recepcionRepository    recepciones de cada orden
     * @param facturaRepository      facturas de cada orden
     * @param usuarioRepository      nombres de quienes decidieron o recibieron
     */
    public SolicitudRecorridoService(SolicitudService solicitudService, RfqRepository rfqRepository,
            CotizacionRepository cotizacionRepository, AdjudicacionRepository adjudicacionRepository,
            OrdenCompraRepository ordenRepository, RecepcionRepository recepcionRepository,
            FacturaProveedorRepository facturaRepository, UsuarioRepository usuarioRepository) {
        this.solicitudService = solicitudService;
        this.rfqRepository = rfqRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.adjudicacionRepository = adjudicacionRepository;
        this.ordenRepository = ordenRepository;
        this.recepcionRepository = recepcionRepository;
        this.facturaRepository = facturaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * @param id id de la solicitud
     * @return pasos de la solicitud, del más viejo al más nuevo
     * @throws com.proyecto.supply_core.common.exception.NoEncontradoException si no existe o no puede verla
     */
    @Transactional(readOnly = true)
    public List<PasoRecorridoResponse> recorrido(Long id) {
        SolicitudResponse s = solicitudService.obtener(id);
        List<PasoRecorridoResponse> pasos = new ArrayList<>();
        pasos.add(new PasoRecorridoResponse(s.creadoEn(), "SOLICITUD", "Solicitud creada", s.consecutivo()));
        if (s.enviadoEn() != null) {
            pasos.add(new PasoRecorridoResponse(s.enviadoEn(), "SOLICITUD", "Enviada a aprobación",
                    s.justificacionExcepcion() == null ? null : "Con excepción: " + s.justificacionExcepcion()));
        }
        Map<Long, String> nombres = nombres(s.aprobaciones().stream().map(AprobacionResponse::usuarioAprobadorId)
                .collect(Collectors.toSet()));
        for (AprobacionResponse a : s.aprobaciones()) {
            String quien = nombres.getOrDefault(a.usuarioAprobadorId(), "—");
            pasos.add(new PasoRecorridoResponse(a.decididoEn(), "APROBACION", textoDecision(a.decision().name()) + " por "
                    + quien, a.observaciones()));
        }

        for (Rfq rfq : rfqRepository.findBySolicitud_IdOrderByCreadoEnAscIdAsc(id)) {
            pasos.add(new PasoRecorridoResponse(rfq.getCreadoEn(), "RFQ", "Cotización pedida " + rfq.getConsecutivo(),
                    "Estado: " + rfq.getEstado()));
            if (rfq.getAbiertaEn() != null) {
                pasos.add(new PasoRecorridoResponse(rfq.getAbiertaEn(), "RFQ", rfq.getConsecutivo() + " abierta a proveedores",
                        null));
            }
            for (Cotizacion c : cotizacionRepository.findByRfq_IdAndEstadoNotOrderByEnviadoEnAsc(rfq.getId(),
                    EstadoCotizacion.BORRADOR)) {
                pasos.add(new PasoRecorridoResponse(c.getEnviadoEn() == null ? c.getCreadoEn() : c.getEnviadoEn(),
                        "COTIZACION", "Cotización de " + c.getProveedor().getRazonSocial(), "Estado: " + c.getEstado()));
            }
            if (rfq.getCerradaEn() != null) {
                pasos.add(new PasoRecorridoResponse(rfq.getCerradaEn(), "RFQ", rfq.getConsecutivo() + " cerrada", null));
            }
            for (Adjudicacion a : adjudicacionRepository.findByRfq_IdOrderByDecididoEnAscIdAsc(rfq.getId())) {
                pasos.add(new PasoRecorridoResponse(a.getDecididoEn(), "ADJUDICACION", "Adjudicada a "
                        + a.getCotizacion().getProveedor().getRazonSocial(), "$ " + pesos(a.getMontoTotal())
                        + " · " + a.getEstado()));
            }
        }

        for (OrdenCompra o : ordenRepository.deSolicitud(id)) {
            pasos.add(new PasoRecorridoResponse(o.getCreadoEn(), "ORDEN", "Orden " + o.getConsecutivo() + " a "
                    + o.getProveedor().getRazonSocial(), "$ " + pesos(o.getTotal()) + " · " + o.getEstado()));
            for (Recepcion r : recepcionRepository.findByOrden_IdOrderByRecibidoEnAscIdAsc(o.getId())) {
                pasos.add(new PasoRecorridoResponse(r.getRecibidoEn(), "RECEPCION", "Mercancía recibida de "
                        + o.getConsecutivo(), r.getObservaciones()));
            }
            for (FacturaProveedor f : facturaRepository.findByOrden_ConsecutivoOrderByCreadoEnAscIdAsc(o.getConsecutivo())) {
                pasos.add(new PasoRecorridoResponse(f.getCreadoEn(), "FACTURA", "Factura " + f.getNumero(),
                        "$ " + pesos(f.getTotal()) + " · " + f.getEstado()));
            }
        }
        pasos.sort(Comparator.comparing(PasoRecorridoResponse::fecha, Comparator.nullsLast(Comparator.naturalOrder())));
        return pasos;
    }

    /**
     * @param ids usuarios
     * @return nombre completo por id
     */
    private Map<Long, String> nombres(Set<Long> ids) {
        Set<Long> validos = new HashSet<>(ids);
        validos.remove(null);
        return usuarioRepository.findAllById(validos).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombreCompleto, (a, b) -> a));
    }

    /**
     * @param decision APROBADA, RECHAZADA o DEVUELTA
     * @return texto para el recorrido
     */
    private static String textoDecision(String decision) {
        return switch (decision) {
            case "APROBADA" -> "Aprobada";
            case "RECHAZADA" -> "Rechazada";
            case "DEVUELTA" -> "Devuelta para corregir";
            default -> decision;
        };
    }

    /**
     * @param valor monto
     * @return monto sin notación científica ("—" si no hay)
     */
    private static String pesos(BigDecimal valor) {
        return valor == null ? "—" : valor.toPlainString();
    }
}
