package com.proyecto.supply_core.venta.dto;

/**
 * Categoría que se muestra como botón en el Punto de venta y en el pedido de venta (spec 027).
 *
 * @param id        categoría
 * @param nombre    nombre
 * @param productos productos activos, vendibles y con precio en la lista General
 */
public record CategoriaParaVender(Long id, String nombre, long productos) {
}
