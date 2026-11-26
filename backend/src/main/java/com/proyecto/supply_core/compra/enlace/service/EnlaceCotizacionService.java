package com.proyecto.supply_core.compra.enlace.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.compra.enlace.dto.EnlaceResponse;
import com.proyecto.supply_core.compra.enlace.entity.EnlaceCotizacion;
import com.proyecto.supply_core.compra.enlace.repository.EnlaceCotizacionRepository;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.entity.RfqProveedor;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.notificacion.email.EmailClient;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Enlaces de un solo uso para que los proveedores invitados coticen sin cuenta (spec 031, D14): se crean al abrir la
 * RFQ, se envían por correo si el proveedor tiene, compras los copia para WhatsApp y los puede regenerar.
 */
@Service
public class EnlaceCotizacionService {

    private static final Logger log = LoggerFactory.getLogger(EnlaceCotizacionService.class);

    private final EnlaceCotizacionRepository enlaceRepository;
    private final RfqRepository rfqRepository;
    private final ProveedorRepository proveedorRepository;
    private final FirmaEnlace firmaEnlace;
    private final EmailClient emailClient;
    private final AuditoriaService auditoriaService;
    private final String urlPublica;

    /**
     * @param enlaceRepository    enlaces
     * @param rfqRepository       RFQ
     * @param proveedorRepository proveedores
     * @param firmaEnlace         arma el texto del enlace
     * @param emailClient         envío del enlace por correo
     * @param auditoriaService    auditoría
     * @param urlPublica          dirección pública de la aplicación ({@code PUBLIC_URL})
     */
    public EnlaceCotizacionService(EnlaceCotizacionRepository enlaceRepository, RfqRepository rfqRepository,
            ProveedorRepository proveedorRepository, FirmaEnlace firmaEnlace, EmailClient emailClient,
            AuditoriaService auditoriaService, @Value("${app.url-publica:http://localhost:4200}") String urlPublica) {
        this.enlaceRepository = enlaceRepository;
        this.rfqRepository = rfqRepository;
        this.proveedorRepository = proveedorRepository;
        this.firmaEnlace = firmaEnlace;
        this.emailClient = emailClient;
        this.auditoriaService = auditoriaService;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    /**
     * Crea un enlace por cada invitado que aún no lo tenga y se lo envía por correo. Lo llama {@code RfqService.abrir}
     * dentro de su transacción.
     *
     * @param rfq RFQ que se acaba de abrir (con invitados)
     */
    @Transactional
    public void crearAlAbrir(Rfq rfq) {
        Long usuarioId = SesionActual.opcional().map(UsuarioAutenticado::id).orElse(null);
        for (RfqProveedor invitado : rfq.getInvitados()) {
            Proveedor p = invitado.getProveedor();
            EnlaceCotizacion e = enlaceRepository.findByRfqIdAndProveedorId(rfq.getId(), p.getId()).orElseGet(() -> {
                EnlaceCotizacion nuevo = new EnlaceCotizacion();
                nuevo.setRfqId(rfq.getId());
                nuevo.setProveedorId(p.getId());
                nuevo.setCreadoPorUsuarioId(usuarioId);
                return nuevo;
            });
            e.setVenceEn(rfq.getFechaLimite());
            enlaceRepository.save(e);
            enviarCorreo(rfq, p, e);
        }
    }

    /**
     * Enlaces de una RFQ con su estado.
     *
     * @param rfqId RFQ
     * @return un enlace por invitado (vacío si la RFQ aún no se abre)
     * @throws NoEncontradoException si la RFQ no existe
     */
    @Transactional(readOnly = true)
    public List<EnlaceResponse> listar(Long rfqId) {
        Rfq rfq = rfqRepository.findById(rfqId).orElseThrow(() -> new NoEncontradoException("RFQ", rfqId));
        return enlaceRepository.findByRfqIdOrderByIdAsc(rfqId).stream().map(e -> respuesta(rfq, e)).toList();
    }

    /**
     * Regenera el enlace de un proveedor: el anterior deja de servir y el nuevo se le envía por correo.
     *
     * @param rfqId       RFQ
     * @param proveedorId proveedor invitado
     * @return enlace nuevo
     * @throws NoEncontradoException si la RFQ o el enlace no existen
     * @throws NegocioException      si la RFQ ya no recibe cotizaciones o el proveedor ya cotizó
     */
    @Transactional
    public EnlaceResponse regenerar(Long rfqId, Long proveedorId) {
        Rfq rfq = rfqRepository.findById(rfqId).orElseThrow(() -> new NoEncontradoException("RFQ", rfqId));
        EnlaceCotizacion e = enlaceRepository.findByRfqIdAndProveedorId(rfqId, proveedorId)
                .orElseThrow(() -> new NoEncontradoException("Enlace de cotización del proveedor", proveedorId));
        if (rfq.getEstado() != EstadoRfq.ABIERTA || !rfq.getFechaLimite().isAfter(LocalDateTime.now())) {
            throw new NegocioException("RFQ_NO_RECIBE_COTIZACIONES",
                    "La " + rfq.getConsecutivo() + " ya no recibe cotizaciones");
        }
        if (e.getUsadoEn() != null) {
            throw new NegocioException("ENLACE_USADO", "El proveedor ya envió su cotización desde el enlace");
        }
        e.setVersion(e.getVersion() + 1);
        e.setVenceEn(rfq.getFechaLimite());
        Proveedor p = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new NoEncontradoException("Proveedor", proveedorId));
        enviarCorreo(rfq, p, e);
        auditoriaService.exito(AccionAuditoria.ENLACE_COTIZACION_REGENERAR, EntidadAuditada.RFQ, rfqId,
                rfq.getConsecutivo() + "; proveedor=" + p.getRazonSocial() + "; version=" + e.getVersion());
        return respuesta(rfq, e);
    }

    /**
     * Dirección completa del enlace.
     *
     * @param e enlace guardado (con id)
     * @return URL de la página pública
     */
    public String url(EnlaceCotizacion e) {
        Long empresaId = e.getEmpresaId() != null ? e.getEmpresaId() : EmpresaContexto.requerida();
        return urlPublica + "/cotizar/" + firmaEnlace.token(e.getId(), empresaId, e.getVersion());
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Respuesta con estado calculado.
     *
     * @param rfq RFQ
     * @param e   enlace
     * @return DTO
     */
    private EnlaceResponse respuesta(Rfq rfq, EnlaceCotizacion e) {
        Proveedor p = proveedorRepository.findById(e.getProveedorId()).orElseThrow();
        String estado;
        if (e.getUsadoEn() != null) {
            estado = "USADO";
        } else if (rfq.getEstado() != EstadoRfq.ABIERTA || !e.getVenceEn().isAfter(LocalDateTime.now())) {
            estado = "VENCIDO";
        } else {
            estado = "PENDIENTE";
        }
        return new EnlaceResponse(p.getId(), p.getRazonSocial(), p.getCorreoContacto(), p.getTelefono(),
                "PENDIENTE".equals(estado) ? url(e) : null, estado, e.getVenceEn(), e.getUsadoEn());
    }

    /**
     * Envía el enlace al correo del proveedor, si tiene.
     *
     * @param rfq RFQ
     * @param p   proveedor
     * @param e   enlace guardado
     */
    private void enviarCorreo(Rfq rfq, Proveedor p, EnlaceCotizacion e) {
        if (p.getCorreoContacto() == null || p.getCorreoContacto().isBlank()) {
            return;
        }
        emailClient.enviar(p.getCorreoContacto(), "Solicitud de cotización " + rfq.getConsecutivo(),
                "Hola " + p.getRazonSocial() + ",\n\nLo invitamos a cotizar la " + rfq.getConsecutivo() + " ("
                        + rfq.getLineas().size() + " productos) hasta el " + rfq.getFechaLimite() + ".\n"
                        + "Responda desde este enlace, sin crear una cuenta:\n" + url(e)
                        + "\n\nEl enlace sirve una sola vez y vence con la fecha límite.");
        log.info("Enlace de cotización de la {} enviado a {}", rfq.getConsecutivo(), p.getRazonSocial());
    }
}
