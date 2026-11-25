package com.proyecto.supply_core.usuario.dto;

/**
 * Un permiso en la ficha de una persona (spec 022): si se lo da su rol, si se le sumó o quitó a mano y si al final lo
 * tiene.
 *
 * @param codigo   código del permiso
 * @param nombre   qué permite
 * @param modulo   módulo en el que se agrupa
 * @param delRol   {@code true} si alguno de sus roles lo da
 * @param ajuste   {@code SUMADO}, {@code QUITADO} o {@code null} si va como dice su rol
 * @param efectivo {@code true} si al final lo tiene
 */
public record PermisoUsuarioResponse(String codigo, String nombre, String modulo, boolean delRol, String ajuste,
        boolean efectivo) {
}
