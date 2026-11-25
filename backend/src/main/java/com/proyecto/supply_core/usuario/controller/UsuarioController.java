package com.proyecto.supply_core.usuario.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.security.annotation.RequierePermiso;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.usuario.dto.AjustePermisosRequest;
import com.proyecto.supply_core.usuario.dto.PermisoUsuarioResponse;
import com.proyecto.supply_core.usuario.dto.RestablecerPasswordRequest;
import com.proyecto.supply_core.usuario.dto.UsuarioRequest;
import com.proyecto.supply_core.usuario.dto.UsuarioResponse;
import com.proyecto.supply_core.usuario.dto.UsuarioUpdateRequest;
import com.proyecto.supply_core.usuario.service.UsuarioPermisoService;
import com.proyecto.supply_core.usuario.service.UsuarioService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Administración de usuarios: solo ADMIN_SISTEMA (el AUDITOR puede consultar). */
@RestController
@RequestMapping("/api/usuarios")
@RequierePermiso(Permisos.USUARIO_GESTIONAR)
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioPermisoService permisoService;

    /**
     * @param usuarioService lógica de usuarios
     * @param permisoService permisos sueltos de cada persona (spec 022)
     */
    public UsuarioController(UsuarioService usuarioService, UsuarioPermisoService permisoService) {
        this.usuarioService = usuarioService;
        this.permisoService = permisoService;
    }

    /**
     * Lista usuarios con filtros y paginación ({@code ?page=0&size=20&sort=correo,desc}).
     *
     * @param texto    coincidencia en nombre o correo
     * @param activo   estado
     * @param rol      código de rol
     * @param pageable página (máximo 100 por página) y orden
     * @return página de usuarios
     */
    @GetMapping
    @RequierePermiso(Permisos.USUARIO_VER)
    public PageResponse<UsuarioResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) String rol,
            @PageableDefault(size = 20, sort = "nombreCompleto", direction = Sort.Direction.ASC) Pageable pageable) {
        return usuarioService.buscar(texto, activo, rol, pageable);
    }

    /**
     * Detalle de un usuario.
     *
     * @param id id positivo
     * @return usuario
     */
    @GetMapping("/{id}")
    @RequierePermiso(Permisos.USUARIO_VER)
    public UsuarioResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return usuarioService.obtener(id);
    }

    /**
     * Crea un usuario.
     *
     * @param request datos validados
     * @return usuario creado (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody UsuarioRequest request) {
        return usuarioService.crear(request);
    }

    /**
     * Edita datos y roles.
     *
     * @param id      id positivo
     * @param request datos validados
     * @return usuario actualizado
     */
    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody UsuarioUpdateRequest request) {
        return usuarioService.actualizar(id, request);
    }

    /**
     * Reactiva un usuario.
     *
     * @param id id positivo
     * @return usuario activo
     */
    @PatchMapping("/{id}/activar")
    public UsuarioResponse activar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return usuarioService.activar(id);
    }

    /**
     * Desactiva un usuario.
     *
     * @param id id positivo
     * @return usuario inactivo
     */
    @PatchMapping("/{id}/desactivar")
    public UsuarioResponse desactivar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return usuarioService.desactivar(id);
    }

    /**
     * Quita el segundo factor de un usuario que perdió su teléfono y le corta las sesiones.
     * Si es ADMIN_SISTEMA, vinculará una app nueva en su próximo login.
     *
     * @param id id positivo
     */
    @PatchMapping("/{id}/mfa/reiniciar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reiniciarMfa(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        usuarioService.reiniciarMfa(id);
    }

    /**
     * Asigna una clave o un PIN nuevo a otro usuario (nunca se devuelve; queda en la auditoría).
     *
     * @param id      id positivo
     * @param request clave o PIN nuevo
     */
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecerPassword(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody RestablecerPasswordRequest request) {
        usuarioService.restablecerPassword(id, request);
    }

    /**
     * Ficha de permisos de una persona (spec 022).
     *
     * @param id persona
     * @return cada permiso con lo que dice su rol, su ajuste y si al final lo tiene
     */
    @GetMapping("/{id}/permisos")
    @RequierePermiso(Permisos.USUARIO_VER)
    public java.util.List<PermisoUsuarioResponse> permisos(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return permisoService.ver(id);
    }

    /**
     * Suma o quita permisos sueltos a una persona (reemplaza los ajustes anteriores).
     *
     * @param id      persona
     * @param request ajustes
     * @return la ficha de permisos actualizada
     */
    @PutMapping("/{id}/permisos")
    public java.util.List<PermisoUsuarioResponse> cambiarPermisos(
            @PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody AjustePermisosRequest request) {
        return permisoService.cambiar(id, request);
    }
}
