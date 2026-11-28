package com.proyecto.supply_core.catalogo.dto;

import com.proyecto.supply_core.catalogo.validation.NombreCategoriaUnico;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de categoría de producto.
 *
 * @param magnitud qué mide su material (spec 038); {@code null} = no lo dice
 * @param nombre nombre único
 */
public record CategoriaRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        @NombreCategoriaUnico
        String nombre,

        com.proyecto.supply_core.catalogo.enums.Magnitud magnitud) {

    /**
     * Sin medida (la categoría no dice qué mide).
     *
     * @param nombre nombre
     */
    public CategoriaRequest(String nombre) {
        this(nombre, null);
    }
}
