package com.proyecto.supply_core.compra.enlace.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionRequest;
import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionResponse;
import com.proyecto.supply_core.compra.cotizacion.service.CotizacionService;
import com.proyecto.supply_core.compra.enlace.EnlaceActual.CotizacionPublicaErrores;
import com.proyecto.supply_core.compra.enlace.dto.CotizacionPublicaResponse;
import com.proyecto.supply_core.compra.enlace.dto.EnvioPublicoRequest;
import com.proyecto.supply_core.compra.enlace.entity.EnlaceCotizacion;
import com.proyecto.supply_core.compra.enlace.repository.EnlaceCotizacionRepository;
import com.proyecto.supply_core.compra.rfq.dto.RfqPortalResponse;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.Modulos;

/**
 * Cotizar desde el enlace, sin cuenta (spec 031). La empresa ya quedó fijada por el enlace; aquí se revisa que siga
 * vigente (sin usar, sin vencer, RFQ abierta, módulo activo) y se reusa {@link CotizacionService} con el proveedor del
 * enlace. Todo enlace que no sirve responde el mismo 404.
 */
@Service
public class CotizacionPublicaService {

    private static final Logger log = LoggerFactory.getLogger(CotizacionPublicaService.class);

    private final EnlaceCotizacionRepository enlaceRepository;
    private final RfqRepository rfqRepository;
    private final ProveedorRepository proveedorRepository;
    private final EmpresaRepository empresaRepository;
    private final CotizacionService cotizacionService;
    private final TurnstileVerificador turnstile;
    private final AccesoEmpresaService accesoEmpresa;

    /**
     * @param enlaceRepository    enlaces
     * @param rfqRepository       RFQ
     * @param proveedorRepository proveedores
     * @param empresaRepository   nombre de la empresa
     * @param cotizacionService   lógica de cotizaciones
     * @param turnstile           verificación anti-bot
     * @param accesoEmpresa       suscripción y módulo de la empresa
     */
    public CotizacionPublicaService(EnlaceCotizacionRepository enlaceRepository, RfqRepository rfqRepository,
            ProveedorRepository proveedorRepository, EmpresaRepository empresaRepository,
            CotizacionService cotizacionService, TurnstileVerificador turnstile, AccesoEmpresaService accesoEmpresa) {
        this.enlaceRepository = enlaceRepository;
        this.rfqRepository = rfqRepository;
        this.proveedorRepository = proveedorRepository;
        this.empresaRepository = empresaRepository;
        this.cotizacionService = cotizacionService;
        this.turnstile = turnstile;
        this.accesoEmpresa = accesoEmpresa;
    }

    /**
     * Datos para la página del enlace.
     *
     * @param enlaceId enlace validado por su firma
     * @return empresa, proveedor, RFQ y borrador
     */
    @Transactional(readOnly = true)
    public CotizacionPublicaResponse ver(Long enlaceId) {
        EnlaceCotizacion e = vigente(enlaceRepository.findById(enlaceId).orElse(null), "GET");
        Rfq rfq = rfqRepository.findWithDetalleById(e.getRfqId()).orElseThrow(CotizacionPublicaErrores::noValido);
        String empresa = empresaRepository.findById(e.getEmpresaId()).map(x -> x.getRazonSocial()).orElse("");
        String proveedor = proveedorRepository.findById(e.getProveedorId()).map(p -> p.getRazonSocial()).orElse("");
        return new CotizacionPublicaResponse(empresa, proveedor, RfqPortalResponse.of(rfq),
                cotizacionService.deProveedor(e.getRfqId(), e.getProveedorId()), turnstile.claveSitio());
    }

    /**
     * Guarda el borrador.
     *
     * @param enlaceId enlace validado
     * @param req      precios y condiciones
     * @return borrador con montos calculados
     */
    @Transactional
    public CotizacionResponse guardar(Long enlaceId, CotizacionRequest req) {
        EnlaceCotizacion e = vigente(enlaceRepository.findById(enlaceId).orElse(null), "PUT");
        return cotizacionService.guardar(e.getRfqId(), e.getProveedorId(), req);
    }

    /**
     * Adjunta el documento de la cotización.
     *
     * @param enlaceId enlace validado
     * @param archivo  PDF, Excel o imagen
     * @return borrador con el documento
     */
    @Transactional
    public CotizacionResponse subirArchivo(Long enlaceId, MultipartFile archivo) {
        EnlaceCotizacion e = vigente(enlaceRepository.findById(enlaceId).orElse(null), "POST");
        return cotizacionService.subirArchivoDesdeEnlace(e.getRfqId(), e.getProveedorId(), archivo);
    }

    /**
     * Envía la cotización y cierra el enlace. Si llega el campo trampa con valor, responde igual sin guardar nada.
     *
     * @param enlaceId enlace validado
     * @param req      token anti-bot y campo trampa
     * @param ip       IP del cliente
     */
    @Transactional
    public void enviar(Long enlaceId, EnvioPublicoRequest req, String ip) {
        EnlaceCotizacion e = vigente(enlaceRepository.bloquear(enlaceId).orElse(null), "POST");
        if (req != null && req.sitioWeb() != null && !req.sitioWeb().isBlank()) {
            log.warn("Envío descartado desde el enlace {}: llegó el campo trampa (IP {})", enlaceId, ip);
            return;
        }
        turnstile.verificar(req == null ? null : req.turnstileToken(), ip);
        cotizacionService.enviar(e.getRfqId(), e.getProveedorId());
        e.setUsadoEn(LocalDateTime.now());
    }

    /**
     * Exige que el enlace sirva: existe, sin usar, sin vencer, RFQ abierta y la empresa con el módulo activo.
     *
     * @param e      enlace (o {@code null})
     * @param metodo método HTTP (para la suscripción morosa)
     * @return el mismo enlace
     * @throws com.proyecto.supply_core.common.exception.NoEncontradoException el mismo 404 si no sirve
     */
    private EnlaceCotizacion vigente(EnlaceCotizacion e, String metodo) {
        LocalDateTime ahora = LocalDateTime.now();
        if (e == null || e.getUsadoEn() != null || !e.getVenceEn().isAfter(ahora)) {
            throw CotizacionPublicaErrores.noValido();
        }
        Rfq rfq = rfqRepository.findById(e.getRfqId()).orElseThrow(CotizacionPublicaErrores::noValido);
        if (rfq.getEstado() != EstadoRfq.ABIERTA || !rfq.getFechaLimite().isAfter(ahora)) {
            throw CotizacionPublicaErrores.noValido();
        }
        try {
            accesoEmpresa.exigirSuscripcion(e.getEmpresaId(), metodo);
            accesoEmpresa.exigirModulo(e.getEmpresaId(), Modulos.RFQ_COTIZACIONES);
        } catch (AccesoDenegadoException ex) {
            throw CotizacionPublicaErrores.noValido();
        }
        return e;
    }
}
