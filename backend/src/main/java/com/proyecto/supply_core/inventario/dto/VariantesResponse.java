package com.proyecto.supply_core.inventario.dto;

import java.util.List;

/**
 * Un producto con variantes (spec 028): el producto general con su stock total (suma informativa de sus variantes), sus
 * atributos y sus variantes con su propio stock, activas e inactivas.
 *
 * @param agrupador producto general
 * @param atributos atributos con sus valores
 * @param variantes variantes, por código
 */
public record VariantesResponse(ProductoResponse agrupador, List<AtributoVariante> atributos,
        List<ProductoResponse> variantes) {
}
