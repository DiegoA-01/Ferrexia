package com.proyecto.supply_core.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edición de categoría. El nombre único se valida en el Service (excluye a la propia categoría).
 *
 * @param magnitud qué mide su material (spec 038); {@code null} = no lo dice
 * @param nombre nombre nuevo
 */
public record CategoriaUpdateRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        String nombre,

        com.proyecto.supply_core.catalogo.enums.Magnitud magnitud) {

    /**
     * Sin medida (la categoría no dice qué mide).
     *
     * @param nombre nombre
     */
    public CategoriaUpdateRequest(String nombre) {
        this(nombre, null);
    }
}
