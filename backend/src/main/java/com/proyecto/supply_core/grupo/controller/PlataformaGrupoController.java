package com.proyecto.supply_core.grupo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.grupo.dto.GrupoDtos.AsignacionGrupoResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.AsignarGrupoRequest;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.GrupoCrearRequest;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.GrupoResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.SuperAdminCrearRequest;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.SuperAdminEstadoRequest;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.SuperAdminResponse;
import com.proyecto.supply_core.grupo.service.GrupoPlataformaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Consola de la plataforma: grupos de empresas, sus empresas y sus super administradores (spec 035). Solo con el token
 * de la plataforma (el {@code AuthFilter} rechaza cualquier otro en {@code /api/plataforma/**}); no lleva
 * {@code @RequierePermiso} porque no es una API de empresa y no lee datos de negocio.
 */
@RestController
@RequestMapping("/api/plataforma")
public class PlataformaGrupoController {

    private final GrupoPlataformaService grupoService;

    /**
     * @param grupoService lógica de grupos de la plataforma
     */
    public PlataformaGrupoController(GrupoPlataformaService grupoService) {
        this.grupoService = grupoService;
    }

    /**
     * Lista los grupos con sus empresas y sus super administradores.
     *
     * @return grupos, el más nuevo primero
     */
    @GetMapping("/grupos")
    public List<GrupoResponse> listar() {
        return grupoService.listar();
    }

    /**
     * Crea un grupo vacío.
     *
     * @param request nombre del grupo
     * @return grupo creado (HTTP 201)
     */
    @PostMapping("/grupos")
    @ResponseStatus(HttpStatus.CREATED)
    public GrupoResponse crear(@Valid @RequestBody GrupoCrearRequest request) {
        return grupoService.crear(request.nombre());
    }

    /**
     * Asigna una empresa a un grupo, la cambia de grupo o la saca de él.
     *
     * @param id      id positivo de la empresa
     * @param request {@code grupoId} del grupo nuevo, o {@code null} para sacarla de su grupo
     * @return la empresa y su grupo actual
     */
    @PutMapping("/empresas/{id}/grupo")
    public AsignacionGrupoResponse asignarEmpresa(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @RequestBody AsignarGrupoRequest request) {
        return grupoService.asignarEmpresa(id, request.grupoId());
    }

    /**
     * Elimina un grupo sin empresas ni traslados, junto con sus super administradores.
     *
     * @param id id positivo del grupo
     */
    @DeleteMapping("/grupos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        grupoService.eliminar(id);
    }

    /**
     * Crea al super administrador de un grupo y lo vincula en cada empresa del grupo.
     *
     * @param id      id positivo del grupo
     * @param request nombre, correo y clave fuerte
     * @return super administrador creado (HTTP 201)
     */
    @PostMapping("/grupos/{id}/super-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public SuperAdminResponse crearSuperAdmin(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody SuperAdminCrearRequest request) {
        return grupoService.crearSuperAdmin(id, request);
    }

    /**
     * Activa o desactiva a un super administrador (al desactivarlo se cortan sus sesiones).
     *
     * @param id      id positivo del grupo
     * @param usuario id positivo del super administrador
     * @param request estado nuevo
     * @return super administrador con su estado actual
     */
    @PatchMapping("/grupos/{id}/super-admin/{usuario}")
    public SuperAdminResponse cambiarEstado(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @PathVariable @Positive(message = "debe ser un id válido") Long usuario,
            @Valid @RequestBody SuperAdminEstadoRequest request) {
        return grupoService.cambiarEstado(id, usuario, request.activo());
    }

    /**
     * Reinicia el segundo factor de un super administrador: en su siguiente ingreso lo configura de nuevo, y sus
     * sesiones abiertas se cortan.
     *
     * @param id      id positivo del grupo
     * @param usuario id positivo del super administrador
     * @return super administrador con su estado actual
     */
    @PostMapping("/grupos/{id}/super-admin/{usuario}/reiniciar-mfa")
    public SuperAdminResponse reiniciarMfa(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @PathVariable @Positive(message = "debe ser un id válido") Long usuario) {
        return grupoService.reiniciarMfa(id, usuario);
    }
}
