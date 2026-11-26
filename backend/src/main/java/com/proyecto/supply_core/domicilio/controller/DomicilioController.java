package com.proyecto.supply_core.domicilio.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.domicilio.dto.DomicilioDtos;
import com.proyecto.supply_core.domicilio.entity.Domicilio;
import com.proyecto.supply_core.domicilio.service.DomicilioService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequierePermiso;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Permisos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Domicilios (spec 032). Listar y ver: quien gestiona o cobra ve todos y el domiciliario solo los suyos (lo decide el
 * servicio); asignar, devolver, cancelar y configurar el envío: quien gestiona; cobrar la contraentrega: quien cobra.
 */
@RequiereModulo(Modulos.VENTAS)
@RestController
@RequestMapping("/api/domicilios")
@UsuarioInterno
@Validated
public class DomicilioController {

    private final DomicilioService domicilioService;

    /**
     * @param domicilioService lógica de domicilios
     */
    public DomicilioController(DomicilioService domicilioService) {
        this.domicilioService = domicilioService;
    }

    /**
     * @param fecha  día (vacío = hoy)
     * @param estado estado (opcional)
     * @return domicilios del día
     */
    @GetMapping
    public List<DomicilioDtos.Respuesta> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Domicilio.Estado estado) {
        return domicilioService.listar(fecha, estado);
    }

    /**
     * @param id domicilio
     * @return el domicilio con su historial
     */
    @GetMapping("/{id:\\d+}")
    public DomicilioDtos.Respuesta obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return domicilioService.obtener(id);
    }

    /**
     * @param id  domicilio
     * @param req domiciliario interno o externo
     * @return el domicilio asignado
     */
    @PostMapping("/{id:\\d+}/asignar")
    @RequierePermiso(Permisos.DOMICILIO_GESTIONAR)
    public DomicilioDtos.Respuesta asignar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody DomicilioDtos.Asignar req) {
        return domicilioService.asignar(id, req);
    }

    /**
     * @param id  domicilio
     * @param req estado nuevo y nota
     * @return el domicilio
     */
    @PostMapping("/{id:\\d+}/estado")
    public DomicilioDtos.Respuesta cambiarEstado(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody DomicilioDtos.CambioEstado req) {
        return domicilioService.cambiarEstado(id, req);
    }

    /**
     * @param id  domicilio contraentrega entregado
     * @param req pagos que suman el total
     * @return el domicilio cobrado
     */
    @PostMapping("/{id:\\d+}/cobrar")
    @RequierePermiso(Permisos.VENTA_COBRAR)
    public DomicilioDtos.Respuesta cobrar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody DomicilioDtos.Cobro req) {
        return domicilioService.cobrar(id, req);
    }

    /**
     * @return el producto del envío y su precio, o vacío si no está configurado
     */
    @GetMapping("/envio")
    public DomicilioDtos.Envio envio() {
        return domicilioService.envio();
    }

    /**
     * @param req precio del envío en la lista General
     * @return el envío configurado
     */
    @PutMapping("/envio")
    @RequierePermiso(Permisos.DOMICILIO_GESTIONAR)
    public DomicilioDtos.Envio configurarEnvio(@Valid @RequestBody DomicilioDtos.EnvioPrecio req) {
        return domicilioService.configurarEnvio(req);
    }

    /**
     * @return usuarios que pueden repartir domicilios
     */
    @GetMapping("/domiciliarios")
    @RequierePermiso(Permisos.DOMICILIO_GESTIONAR)
    public List<DomicilioDtos.Domiciliario> domiciliarios() {
        return domicilioService.domiciliarios();
    }
}
