package com.proyecto.supply_core.usuario.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequierePermiso;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.usuario.dto.PermisoResponse;
import com.proyecto.supply_core.usuario.dto.RolRequest;
import com.proyecto.supply_core.usuario.dto.RolResponse;
import com.proyecto.supply_core.usuario.service.RolService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Roles de la empresa (spec 022): las plantillas del sistema y los roles propios. Los ve quien consulta usuarios; los
 * crea, edita y borra quien tiene {@code ROL_GESTIONAR}.
 */
@RestController
@RequestMapping("/api/roles")
@RequierePermiso(Permisos.ROL_VER)
public class RolController {

    private final RolService rolService;

    /**
     * @param rolService lógica de roles
     */
    public RolController(RolService rolService) {
        this.rolService = rolService;
    }

    /**
     * @return plantillas y roles propios, por nombre, con sus permisos
     */
    @GetMapping
    public List<RolResponse> listar() {
        return rolService.listar();
    }

    /**
     * @return catálogo de permisos por módulo
     */
    @GetMapping("/permisos")
    public List<PermisoResponse> permisos() {
        return rolService.permisos();
    }

    /**
     * @param request nombre, descripción y permisos
     * @return rol propio creado (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequierePermiso(Permisos.ROL_GESTIONAR)
    public RolResponse crear(@Valid @RequestBody RolRequest request) {
        return rolService.crear(request);
    }

    /**
     * @param id      rol propio
     * @param request datos nuevos
     * @return rol actualizado
     */
    @PutMapping("/{id}")
    @RequierePermiso(Permisos.ROL_GESTIONAR)
    public RolResponse editar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody RolRequest request) {
        return rolService.editar(id, request);
    }

    /**
     * @param id rol propio sin personas
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequierePermiso(Permisos.ROL_GESTIONAR)
    public void eliminar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        rolService.eliminar(id);
    }
}
