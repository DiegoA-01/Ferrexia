package com.proyecto.supply_core.domicilio.service;

import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.domicilio.dto.DomicilioVentaRequest;
import com.proyecto.supply_core.domicilio.entity.Domicilio;
import com.proyecto.supply_core.domicilio.entity.DomicilioEvento;
import com.proyecto.supply_core.domicilio.repository.DomicilioEventoRepository;
import com.proyecto.supply_core.domicilio.repository.DomicilioRepository;

/**
 * Crea el domicilio de una venta y anota sus cambios (spec 032). Solo usa repositorios, así la venta y la anulación lo
 * llaman sin depender de {@code DomicilioService}.
 */
@Component
public class DomicilioRegistro {

    private final DomicilioRepository domicilioRepository;
    private final DomicilioEventoRepository eventoRepository;
    private final ConsecutivoGenerator consecutivos;

    /**
     * @param domicilioRepository domicilios
     * @param eventoRepository    historial
     * @param consecutivos        consecutivo {@code DOM}
     */
    public DomicilioRegistro(DomicilioRepository domicilioRepository, DomicilioEventoRepository eventoRepository,
            ConsecutivoGenerator consecutivos) {
        this.domicilioRepository = domicilioRepository;
        this.eventoRepository = eventoRepository;
        this.consecutivos = consecutivos;
    }

    /**
     * @param ventaId       venta recién confirmada
     * @param datos         dirección, teléfono y referencia
     * @param contraentrega {@code true} si se paga al entregar
     * @param usuarioId     quien vendió
     * @return el domicilio PENDIENTE
     */
    public Domicilio crear(Long ventaId, DomicilioVentaRequest datos, boolean contraentrega, Long usuarioId) {
        Domicilio d = new Domicilio();
        d.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.DOMICILIO));
        d.setVentaId(ventaId);
        d.setDireccion(datos.direccion().trim());
        d.setTelefono(datos.telefono().trim());
        d.setReferencia(datos.referencia() == null || datos.referencia().isBlank() ? null : datos.referencia().trim());
        d.setPago(contraentrega ? Domicilio.Pago.CONTRAENTREGA : Domicilio.Pago.PAGADO);
        Domicilio guardado = domicilioRepository.save(d);
        anotar(guardado, Domicilio.Estado.PENDIENTE.name(), usuarioId, contraentrega ? "Contraentrega" : "Pagado");
        return guardado;
    }

    /**
     * La venta del domicilio se anuló por fuera del tablero: si el domicilio no había terminado, queda cancelado.
     *
     * @param ventaId   venta anulada
     * @param usuarioId quien anuló
     * @param motivo    motivo de la anulación
     */
    public void cancelarPorAnulacion(Long ventaId, Long usuarioId, String motivo) {
        domicilioRepository.findByVentaId(ventaId).filter(d -> !d.getEstado().terminal()).ifPresent(d -> {
            d.setEstado(Domicilio.Estado.CANCELADO);
            d.setMotivo(motivo);
            anotar(d, Domicilio.Estado.CANCELADO.name(), usuarioId, "Venta anulada: " + motivo);
        });
    }

    /**
     * @param d         domicilio
     * @param estado    estado que quedó (o {@code COBRADO})
     * @param usuarioId quien lo hizo
     * @param nota      nota o {@code null}
     */
    public void anotar(Domicilio d, String estado, Long usuarioId, String nota) {
        DomicilioEvento e = new DomicilioEvento();
        e.setDomicilioId(d.getId());
        e.setEstado(estado);
        e.setUsuarioId(usuarioId);
        e.setNota(nota == null || nota.isBlank() ? null : nota.trim());
        eventoRepository.save(e);
    }
}
