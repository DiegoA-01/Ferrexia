package com.proyecto.supply_core.inventario.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cambio de los valores de un producto con variantes (spec 028). Los nombres de los atributos no cambian; agregar un
 * valor crea las combinaciones que faltan (con los datos de {@code variantes}) y quitarlo desactiva sus variantes.
 *
 * @param atributos los mismos atributos con su lista nueva de valores
 * @param variantes datos de las combinaciones que todavía no existen (las demás se ignoran)
 */
public record CambioVariantesRequest(
        @NotEmpty(message = "debe tener al menos un atributo")
        @Size(max = 3, message = "máximo 3 atributos")
        List<@Valid AtributoVariante> atributos,

        @NotNull(message = "es obligatorio (puede ir vacío)")
        List<@Valid VarianteNuevaRequest> variantes) {
}
