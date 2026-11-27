package com.proyecto.supply_core.security.context;

/**
 * Super administrador de un grupo que viaja dentro de su JWT y queda disponible durante la petición a
 * {@code /api/grupo/**}. No tiene empresa ni roles de empresa.
 *
 * @param id      id en {@code usuario_grupo}
 * @param grupoId grupo al que pertenece
 * @param correo  correo de login
 * @param nombre  nombre completo
 */
public record GrupoAutenticado(Long id, Long grupoId, String correo, String nombre) {
}
