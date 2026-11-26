package com.proyecto.supply_core.inventario.dto;

import java.util.List;

import com.proyecto.supply_core.catalogo.validation.CategoriaActiva;
import com.proyecto.supply_core.catalogo.validation.UnidadMedidaActiva;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Alta de un producto con variantes (spec 028): el producto general (agrupador) con sus datos comunes, sus atributos y
 * una fila por cada combinación de valores.
 *
 * @param codigo           código del producto general (único; se guarda en mayúsculas)
 * @param nombre           nombre del producto general; el de cada variante suma sus valores
 * @param descripcion      descripción opcional
 * @param categoriaId      categoría activa (la heredan las variantes)
 * @param unidadMedidaId   unidad activa (la heredan las variantes)
 * @param tarifaImpuestoId impuesto de venta (lo heredan las variantes); vacío = IVA 19 % como un producto nuevo
 * @param atributos        de 1 a 3 atributos con sus valores
 * @param variantes        una fila por combinación
 */
public record ProductoConVariantesRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,50}$", message = "de 1 a 50 caracteres: letras, números, '.', '-' o '_'")
        String codigo,

        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "máximo 120 caracteres")
        String nombre,

        @Size(max = 500, message = "máximo 500 caracteres")
        String descripcion,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        @CategoriaActiva
        Long categoriaId,

        @NotNull(message = "es obligatoria")
        @Positive(message = "debe ser un id válido")
        @UnidadMedidaActiva
        Long unidadMedidaId,

        @Positive(message = "debe ser un id válido") Long tarifaImpuestoId,

        @NotEmpty(message = "debe tener al menos un atributo")
        @Size(max = 3, message = "máximo 3 atributos")
        List<@Valid AtributoVariante> atributos,

        @NotEmpty(message = "debe tener al menos una variante")
        List<@Valid VarianteNuevaRequest> variantes) {
}
