package com.proyecto.supply_core.security.context;

import java.util.Set;

/**
 * Datos del usuario que viajan dentro del JWT y quedan disponibles durante la petición.
 *
 * @param id          id del usuario
 * @param correo      correo (login)
 * @param nombre      nombre completo
 * @param roles       códigos de rol (ver {@link Roles})
 * @param proveedorId id del proveedor si es usuario del portal; {@code null} si es interno
 * @param empresaId   empresa (tenant) a la que pertenece; todo lo que consulta queda limitado a ella
 */
public record UsuarioAutenticado(
        Long id,
        String correo,
        String nombre,
        Set<String> roles,
        Long proveedorId,
        Long empresaId) {

    /**
     * Copia defensiva para que los roles no se puedan modificar durante la petición.
     *
     * @param id          id del usuario
     * @param correo      correo
     * @param nombre      nombre completo
     * @param roles       códigos de rol
     * @param proveedorId id del proveedor o {@code null}
     * @param empresaId   empresa del usuario (obligatoria)
     */
    public UsuarioAutenticado {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        if (empresaId == null) {
            throw new IllegalArgumentException("El usuario debe pertenecer a una empresa");
        }
    }

    /**
     * Indica si el usuario tiene al menos uno de los roles.
     *
     * @param codigos códigos de rol aceptados
     * @return {@code true} si tiene alguno
     */
    public boolean tieneAlgunRol(String... codigos) {
        for (String codigo : codigos) {
            if (roles.contains(codigo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indica si la persona tiene un permiso (spec 022): el de sus roles, más los sumados, menos los quitados.
     *
     * @param permiso código del permiso (constante de {@link Permisos})
     * @return {@code true} si lo tiene
     */
    public boolean tienePermiso(String permiso) {
        return com.proyecto.supply_core.security.service.PermisosService.usuarioTiene(this, permiso);
    }

    /**
     * Indica si es un usuario del portal proveedor.
     *
     * @return {@code true} si tiene proveedor asociado
     */
    public boolean esProveedor() {
        return proveedorId != null;
    }
}
