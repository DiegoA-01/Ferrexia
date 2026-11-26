package com.proyecto.supply_core.compra.enlace.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionRequest;
import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionResponse;
import com.proyecto.supply_core.compra.enlace.EnlaceActual;
import com.proyecto.supply_core.compra.enlace.dto.CotizacionPublicaResponse;
import com.proyecto.supply_core.compra.enlace.dto.EnvioPublicoRequest;
import com.proyecto.supply_core.compra.enlace.service.CotizacionPublicaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Cotizar desde un enlace, sin cuenta (spec 031). Público: el {@code AuthFilter} valida la firma del enlace, fija su
 * empresa y lo deja en la petición; un enlace que no sirve responde siempre el mismo 404. Tiene límite de peticiones.
 */
@RestController
@RequestMapping("/api/publico/cotizar/{token}")
public class CotizacionPublicaController {

    private final CotizacionPublicaService service;

    /**
     * @param service lógica de la cotización por enlace
     */
    public CotizacionPublicaController(CotizacionPublicaService service) {
        this.service = service;
    }

    /**
     * Empresa, RFQ y borrador del proveedor del enlace.
     *
     * @param token texto del enlace (ya validado por el filtro)
     * @return datos para la página
     */
    @GetMapping
    public CotizacionPublicaResponse ver(@PathVariable String token) {
        return service.ver(EnlaceActual.enlace().id());
    }

    /**
     * Guarda el borrador (precios, impuesto y plazo).
     *
     * @param token   texto del enlace
     * @param request datos y precios
     * @return borrador con montos calculados
     */
    @PutMapping
    public CotizacionResponse guardar(@PathVariable String token, @Valid @RequestBody CotizacionRequest request) {
        return service.guardar(EnlaceActual.enlace().id(), request);
    }

    /**
     * Adjunta el documento de la cotización (multipart, campo {@code archivo}).
     *
     * @param token   texto del enlace
     * @param archivo PDF, Excel o imagen (máx. 10 MB)
     * @return borrador con el documento
     */
    @PostMapping(path = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CotizacionResponse subirArchivo(@PathVariable String token, @RequestPart("archivo") MultipartFile archivo) {
        return service.subirArchivo(EnlaceActual.enlace().id(), archivo);
    }

    /**
     * Envía la cotización; el enlace queda cerrado.
     *
     * @param token   texto del enlace
     * @param request token anti-bot y campo trampa
     * @param http    petición (IP del cliente)
     * @return 204
     */
    @PostMapping("/enviar")
    public ResponseEntity<Void> enviar(@PathVariable String token, @Valid @RequestBody EnvioPublicoRequest request,
            HttpServletRequest http) {
        service.enviar(EnlaceActual.enlace().id(), request, http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
