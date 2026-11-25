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

import com.proyecto.supply_core.caja.dto.CategoriaMovimientoCajaCambio;
import com.proyecto.supply_core.caja.dto.CategoriaMovimientoCajaRequest;
import com.proyecto.supply_core.caja.dto.CategoriaMovimientoCajaResponse;
import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;
import com.proyecto.supply_core.caja.service.CategoriaMovimientoCajaService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Categorías de ingresos y egresos de caja (spec 021). Las administran el dueño y el supervisor de ventas; el cajero
 * las consulta para elegir una al registrar un gasto o un ingreso.
 */
@RequiereModulo(Modulos.CAJA)
@RestController
@RequestMapping("/api/cajas/categorias-movimiento")
@UsuarioInterno
@RequiereRol({ Roles.ADMIN_SISTEMA, Roles.SUPERVISOR_VENTAS })
public class CategoriaMovimientoCajaController {

    private final CategoriaMovimientoCajaService service;

    /**
     * @param service lógica de categorías
     */
    public CategoriaMovimientoCajaController(CategoriaMovimientoCajaService service) {
        this.service = service;
    }

    /**
     * @param tipo   filtro por tipo (vacío = los dos)
     * @param activa filtro por estado (vacío = todas)
     * @return categorías
     */
    @GetMapping
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.SUPERVISOR_VENTAS, Roles.CAJERO, Roles.AUDITOR })
    public List<CategoriaMovimientoCajaResponse> listar(@RequestParam(required = false) TipoCategoriaMovimiento tipo,
            @RequestParam(required = false) Boolean activa) {
        return service.listar(tipo, activa);
    }

    /**
     * @param request tipo y nombre
     * @return categoría creada (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaMovimientoCajaResponse crear(@Valid @RequestBody CategoriaMovimientoCajaRequest request) {
        return service.crear(request);
    }

    /**
     * @param id      categoría
     * @param request nombre y estado
     * @return categoría actualizada
     */
    @PutMapping("/{id}")
    public CategoriaMovimientoCajaResponse editar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody CategoriaMovimientoCajaCambio request) {
        return service.editar(id, request);
    }
}
