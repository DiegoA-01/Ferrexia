package com.proyecto.supply_core.inventario.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Atributo de un producto con variantes (spec 028), por ejemplo Medida con 1", 1 1/2" y 2".
 *
 * @param nombre  nombre del atributo (Medida, Color, Calibre...)
 * @param valores valores en el orden en que se muestran
 */
public record AtributoVariante(
        @NotBlank(message = "es obligatorio")
        @Size(max = 40, message = "máximo 40 caracteres")
        String nombre,

        @NotEmpty(message = "debe tener al menos un valor")
        @Size(max = 30, message = "máximo 30 valores")
        List<@NotBlank(message = "no puede estar vacío") @Size(max = 40, message = "máximo 40 caracteres") String> valores) {
}
