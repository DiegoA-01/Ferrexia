package com.proyecto.supply_core.grupo.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.grupo.dto.GrupoDtos.EmpresaEntrableResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.TableroGrupoResponse;
import com.proyecto.supply_core.grupo.service.GrupoEmpresaService;
import com.proyecto.supply_core.grupo.service.GrupoTableroService;
import com.proyecto.supply_core.security.dto.LoginResponse;

import jakarta.validation.constraints.Positive;

/**
 * Zona del super administrador de un grupo (spec 035): sus empresas, entrar a una de ellas y el tablero del grupo. Solo
 * con el token de grupo (el {@code AuthFilter} rechaza cualquier otro en {@code /api/grupo/**}); no lleva
 * {@code @RequierePermiso} porque no es una API de empresa: trabaja sin empresa y entra a cada una con
 * {@code EmpresaContexto.como}.
 */
@RestController
@RequestMapping("/api/grupo")
public class GrupoController {

    private final GrupoEmpresaService empresaService;
    private final GrupoTableroService tableroService;

    /**
     * @param empresaService empresas del grupo y entrada a cada una
     * @param tableroService tablero del grupo
     */
    public GrupoController(GrupoEmpresaService empresaService, GrupoTableroService tableroService) {
        this.empresaService = empresaService;
        this.tableroService = tableroService;
    }

    /**
     * Empresas del grupo a las que puede entrar.
     *
     * @return empresas del grupo
     */
    @GetMapping("/empresas")
    public List<EmpresaEntrableResponse> empresas() {
        return empresaService.empresas();
    }

    /**
     * Entra a una empresa del grupo sin volver a escribir la clave: devuelve una sesión normal de esa empresa (JWT y
     * refresh token). Una empresa de otro grupo o sin vínculo responde 404.
     *
     * @param empresaId id positivo de la empresa
     * @return sesión de la empresa
     */
    @PostMapping("/entrar/{empresaId}")
    public LoginResponse entrar(@PathVariable @Positive(message = "debe ser un id válido") Long empresaId) {
        return empresaService.entrar(empresaId);
    }

    /**
     * Tablero del grupo: ventas netas, compras e inventario valorizado por empresa y en total.
     *
     * @param desde primer día (vacío = primer día del mes en curso)
     * @param hasta último día (vacío = hoy)
     * @return cifras por empresa y total
     */
    @GetMapping("/tablero")
    public TableroGrupoResponse tablero(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return tableroService.tablero(desde, hasta);
    }
}
