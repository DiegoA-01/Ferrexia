package com.proyecto.supply_core.compra.enlace.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.compra.enlace.dto.EnlaceResponse;
import com.proyecto.supply_core.compra.enlace.service.EnlaceCotizacionService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequierePermiso;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Permisos;

import jakarta.validation.constraints.Positive;

/**
 * Enlaces para que los proveedores invitados coticen sin cuenta (spec 031). Solo quien gestiona RFQ: el enlace es
 * secreto.
 */
@RequiereModulo(Modulos.RFQ_COTIZACIONES)
@RestController
@RequestMapping("/api/rfqs/{rfqId}/enlaces")
@RequierePermiso(Permisos.RFQ_GESTIONAR)
public class EnlaceCotizacionController {

    private final EnlaceCotizacionService service;

    /**
     * @param service lógica de los enlaces
     */
    public EnlaceCotizacionController(EnlaceCotizacionService service) {
        this.service = service;
    }

    /**
     * Enlaces de la RFQ con su estado (el enlace solo sale mientras sirve).
     *
     * @param rfqId id positivo de la RFQ
     * @return un enlace por invitado
     */
    @GetMapping
    public List<EnlaceResponse> listar(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId) {
        return service.listar(rfqId);
    }

    /**
     * Regenera el enlace de un proveedor (el anterior deja de servir) y se lo envía por correo.
     *
     * @param rfqId       id positivo de la RFQ
     * @param proveedorId id positivo del proveedor invitado
     * @return enlace nuevo
     */
    @PostMapping("/{proveedorId}/regenerar")
    public EnlaceResponse regenerar(@PathVariable @Positive(message = "debe ser un id válido") Long rfqId,
            @PathVariable @Positive(message = "debe ser un id válido") Long proveedorId) {
        return service.regenerar(rfqId, proveedorId);
    }
}
