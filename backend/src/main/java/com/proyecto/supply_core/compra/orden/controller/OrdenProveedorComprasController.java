package com.proyecto.supply_core.compra.orden.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.compra.orden.dto.MotivoOrdenRequest;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.service.OrdenProveedorService;
import com.proyecto.supply_core.exportacion.service.ExportacionService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequierePermiso;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Permisos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * El proveedor ya no tiene portal (spec 031): compras registra sus respuestas a la orden y le envía la orden en PDF
 * por correo.
 */
@RequiereModulo(Modulos.ORDENES_PAGOS)
@RestController
@RequestMapping("/api/ordenes/{id}")
@RequierePermiso(Permisos.ORDEN_GESTIONAR)
public class OrdenProveedorComprasController {

    private final OrdenProveedorService proveedorService;
    private final ExportacionService exportacionService;

    /**
     * @param proveedorService   transiciones del proveedor sobre la orden
     * @param exportacionService PDF de la orden y envío por correo
     */
    public OrdenProveedorComprasController(OrdenProveedorService proveedorService,
            ExportacionService exportacionService) {
        this.proveedorService = proveedorService;
        this.exportacionService = exportacionService;
    }

    /**
     * Registra que el proveedor aceptó, rechazó (con motivo), está preparando o despachó la orden.
     *
     * @param id      id positivo de la orden
     * @param accion  aceptar, rechazar, preparar o despachar
     * @param request motivo, guía o comentario (opcional salvo al rechazar)
     * @return orden actualizada
     */
    @PostMapping("/proveedor/{accion}")
    public OrdenResponse registrar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @PathVariable @Pattern(regexp = "aceptar|rechazar|preparar|despachar",
                    message = "debe ser aceptar, rechazar, preparar o despachar") String accion,
            @Valid @RequestBody(required = false) MotivoOrdenRequest request) {
        return proveedorService.registrarPorCompras(id, accion, request == null ? null : request.observacion());
    }

    /**
     * Envía la orden en PDF al correo del proveedor.
     *
     * @param id id positivo de la orden
     * @return {@code {"para": correo}}
     */
    @PostMapping("/enviar-proveedor")
    public Map<String, String> enviarAlProveedor(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return Map.of("para", exportacionService.enviarOrdenAlProveedor(id));
    }
}
