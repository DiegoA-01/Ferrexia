package com.proyecto.supply_core.caja.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.caja.dto.CajaAsignadaResponse;
import com.proyecto.supply_core.caja.dto.CajaRequest;
import com.proyecto.supply_core.caja.dto.CajaResponse;
import com.proyecto.supply_core.caja.dto.ConfiguracionCaja;
import com.proyecto.supply_core.caja.dto.UsuarioCajaResponse;
import com.proyecto.supply_core.caja.dto.UsuariosCajaRequest;
import com.proyecto.supply_core.caja.service.CajaAsignacionService;
import com.proyecto.supply_core.caja.service.CajaService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequierePermiso;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Permisos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Cajas y su configuración (spec 003). Las crea y edita el dueño; las consultan caja, supervisión y auditoría (el
 * cajero las necesita para abrir su turno).
 */
@RequiereModulo(Modulos.CAJA)
@RestController
@RequestMapping("/api/cajas")
@UsuarioInterno
@RequierePermiso(Permisos.CAJA_GESTIONAR)
public class CajaController {

    private final CajaService cajaService;
    private final CajaAsignacionService asignacionService;

    /**
     * @param cajaService       lógica de cajas
     * @param asignacionService cajas por persona (spec 025)
     */
    public CajaController(CajaService cajaService, CajaAsignacionService asignacionService) {
        this.cajaService = cajaService;
        this.asignacionService = asignacionService;
    }

    /**
     * Cajas activas en las que la sesión puede abrir turno (spec 025): sus cajas asignadas o, si no tiene ninguna,
     * todas.
     *
     * @return cajas con su ocupación
     */
    @GetMapping("/mias")
    @RequierePermiso(Permisos.TURNO_OPERAR)
    public List<CajaResponse> misCajas() {
        return asignacionService.misCajas();
    }

    /**
     * @param id caja
     * @return quiénes la pueden usar (spec 025)
     */
    @GetMapping("/{id}/usuarios")
    @RequierePermiso(Permisos.CAJA_VER)
    public List<UsuarioCajaResponse> usuarios(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return asignacionService.usuariosDe(id);
    }

    /**
     * Cajas asignadas a una persona, para su ficha (spec 025).
     *
     * @param usuarioId persona
     * @return sus cajas; vacío = puede usar todas
     */
    @GetMapping("/de-usuario/{usuarioId}")
    @RequierePermiso(Permisos.CAJA_VER)
    public List<CajaAsignadaResponse> cajasDeUsuario(
            @PathVariable @Positive(message = "debe ser un id válido") Long usuarioId) {
        return asignacionService.cajasAsignadas(usuarioId);
    }

    /**
     * Reemplaza quiénes pueden usar la caja (spec 025).
     *
     * @param id      caja
     * @param request personas
     * @return la lista guardada
     */
    @PutMapping("/{id}/usuarios")
    public List<UsuarioCajaResponse> asignar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody UsuariosCajaRequest request) {
        return asignacionService.asignar(id, request.usuarioIds());
    }

    /**
     * @param activa filtro por estado (vacío = todas)
     * @return cajas
     */
    @GetMapping
    @RequierePermiso(Permisos.CAJA_VER)
    public List<CajaResponse> listar(@RequestParam(required = false) Boolean activa) {
        return cajaService.listar(activa);
    }

    /**
     * @param request datos de la caja
     * @return caja creada (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CajaResponse crear(@Valid @RequestBody CajaRequest request) {
        return cajaService.crear(request);
    }

    /**
     * @param id      caja
     * @param request datos nuevos
     * @return caja actualizada
     */
    @PutMapping("/{id}")
    public CajaResponse editar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody CajaRequest request) {
        return cajaService.editar(id, request);
    }

    /**
     * @return la tolerancia de diferencia de caja
     */
    @GetMapping("/configuracion")
    @RequierePermiso(Permisos.CAJA_CONFIGURACION_VER)
    public ConfiguracionCaja configuracion() {
        return cajaService.configuracion();
    }

    /**
     * @param request tolerancia nueva
     * @return configuración guardada
     */
    @PutMapping("/configuracion")
    public ConfiguracionCaja configurar(@Valid @RequestBody ConfiguracionCaja request) {
        return cajaService.configurar(request);
    }
}
