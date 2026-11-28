package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.validation.CategoriaActiva;
import com.proyecto.supply_core.catalogo.validation.UnidadMedidaActiva;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Edición de producto. El código único se valida en el Service (excluye al propio producto) y la
 * unidad solo se puede cambiar si el producto aún no tiene movimientos.
 *
 * @param codigo          código (se guarda en mayúsculas)
 * @param nombre          nombre visible
 * @param descripcion     descripción opcional
 * @param categoriaId     categoría activa
 * @param unidadMedidaId  unidad activa
 * @param stockMinimo     total mínimo entre bodegas (≥ 0)
 * @param stockMaximo     total máximo opcional (≥ mínimo)
 * @param costoReferencia precio de referencia opcional (≥ 0)
 * @param tarifaImpuestoId      impuesto de venta (spec 026); vacío = no cambia
 * @param proveedorReferenciaId proveedor del costo de referencia (spec 026); vacío = no cambia
 * @param costoReferenciaFecha  fecha del costo de referencia (spec 026); vacío = no cambia
 * @param equivalenciaUnidad    spec 038: al pasar a una unidad que mide otra cosa con movimientos, cuántas unidades nuevas
 *                              equivale 1 unidad actual (1 UND = 1 M); vacío si no hace falta
 */
public record ProductoUpdateRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,50}$", message = "de 1 a 50 caracteres: letras, números, '.', '-' o '_'")
        String codigo,

        @NotBlank(message = "es obligatorio")
        @Size(max = 200, message = "máximo 200 caracteres")
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

        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal stockMinimo,

        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal stockMaximo,

        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal costoReferencia,

        @Positive(message = "debe ser un id válido") Long tarifaImpuestoId,

        @Positive(message = "debe ser un id válido") Long proveedorReferenciaId,

        @jakarta.validation.constraints.PastOrPresent(message = "no puede ser una fecha futura")
        java.time.LocalDate costoReferenciaFecha,

        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 12, fraction = 6, message = "máximo 12 enteros y 6 decimales")
        BigDecimal equivalenciaUnidad) {

    /**
     * Edición sin equivalencia de unidades (como antes de la spec 038).
     *
     * @param codigo                código
     * @param nombre                nombre
     * @param descripcion           descripción
     * @param categoriaId           categoría
     * @param unidadMedidaId        unidad
     * @param stockMinimo           mínimo
     * @param stockMaximo           máximo
     * @param costoReferencia       costo de referencia
     * @param tarifaImpuestoId      impuesto
     * @param proveedorReferenciaId proveedor del costo
     * @param costoReferenciaFecha  fecha del costo
     */
    public ProductoUpdateRequest(String codigo, String nombre, String descripcion, Long categoriaId, Long unidadMedidaId,
            BigDecimal stockMinimo, BigDecimal stockMaximo, BigDecimal costoReferencia, Long tarifaImpuestoId,
            Long proveedorReferenciaId, java.time.LocalDate costoReferenciaFecha) {
        this(codigo, nombre, descripcion, categoriaId, unidadMedidaId, stockMinimo, stockMaximo, costoReferencia,
                tarifaImpuestoId, proveedorReferenciaId, costoReferenciaFecha, null);
    }

    /**
     * Edición sin los datos de la spec 026 (como antes): impuesto, proveedor y fecha no cambian.
     *
     * @param codigo          código
     * @param nombre          nombre
     * @param descripcion     descripción
     * @param categoriaId     categoría
     * @param unidadMedidaId  unidad
     * @param stockMinimo     mínimo
     * @param stockMaximo     máximo
     * @param costoReferencia costo de referencia
     */
    public ProductoUpdateRequest(String codigo, String nombre, String descripcion, Long categoriaId,
            Long unidadMedidaId, BigDecimal stockMinimo, BigDecimal stockMaximo, BigDecimal costoReferencia) {
        this(codigo, nombre, descripcion, categoriaId, unidadMedidaId, stockMinimo, stockMaximo, costoReferencia, null,
                null, null, null);
    }

    /**
     * Regla entre campos: el máximo no puede ser menor que el mínimo.
     *
     * @return {@code true} si no hay máximo o {@code stockMaximo >= stockMinimo}
     */
    @AssertTrue(message = "stockMaximo no puede ser menor que stockMinimo")
    public boolean isRangoStockValido() {
        return stockMaximo == null || stockMinimo == null || stockMaximo.compareTo(stockMinimo) >= 0;
    }
}
