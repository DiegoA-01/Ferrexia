package com.proyecto.supply_core.compra.solicitud.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.proyecto.supply_core.compra.solicitud.enums.ModoProveedor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear o editar una solicitud de compra (UC-04).
 *
 * @param motivo         justificación de la compra
 * @param fechaRequerida fecha en que se necesita (hoy o después)
 * @param lineas         productos y cantidades (al menos una, sin productos repetidos)
 * @param modoProveedor  a quién se le pide (spec 033); {@code null} = abierta a todos los de la categoría
 * @param proveedorIds   proveedores elegidos; obligatorios si es {@link ModoProveedor#ESPECIFICA}
 */
public record SolicitudRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 500, message = "máximo 500 caracteres")
        String motivo,

        @NotNull(message = "es obligatoria")
        @FutureOrPresent(message = "no puede ser una fecha pasada")
        LocalDate fechaRequerida,

        @NotNull(message = "es obligatorio")
        @NotEmpty(message = "debe tener al menos una línea")
        @Size(max = 100, message = "máximo 100 líneas")
        List<@NotNull(message = "no puede estar vacía") @Valid LineaSolicitudRequest> lineas,

        ModoProveedor modoProveedor,

        @Size(max = 20, message = "máximo 20 proveedores")
        Set<@NotNull(message = "no puede estar vacío") @Positive(message = "debe ser un id válido") Long> proveedorIds) {

    /**
     * Constructor sin proveedores (abierta a todos), como antes de la spec 033.
     *
     * @param motivo         justificación
     * @param fechaRequerida fecha en que se necesita
     * @param lineas         productos y cantidades
     */
    public SolicitudRequest(String motivo, LocalDate fechaRequerida, List<LineaSolicitudRequest> lineas) {
        this(motivo, fechaRequerida, lineas, null, null);
    }

    /**
     * Regla del modo: una solicitud a proveedores específicos lleva al menos uno.
     *
     * @return {@code true} si es abierta o trae proveedores
     */
    @AssertTrue(message = "elija al menos un proveedor o deje la solicitud abierta a todos")
    public boolean isProveedoresDelModo() {
        return modoProveedor != ModoProveedor.ESPECIFICA || (proveedorIds != null && !proveedorIds.isEmpty());
    }

    /**
     * Regla entre líneas: un producto aparece una sola vez (se suma la cantidad en una línea).
     *
     * @return {@code true} si no hay productos repetidos (o faltan datos que ya reportan otras reglas)
     */
    @AssertTrue(message = "no puede repetir un producto; sume la cantidad en una sola línea")
    public boolean isLineasSinRepetir() {
        if (lineas == null) {
            return true;
        }
        var ids = lineas.stream().filter(Objects::nonNull).map(LineaSolicitudRequest::productoId)
                .filter(Objects::nonNull).toList();
        return ids.size() == ids.stream().distinct().count();
    }
}
