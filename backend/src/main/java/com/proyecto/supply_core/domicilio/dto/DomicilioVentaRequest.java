package com.proyecto.supply_core.domicilio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos del domicilio al cobrar una venta a domicilio (spec 032).
 *
 * @param direccion  dirección de entrega
 * @param telefono   teléfono de quien recibe
 * @param referencia indicaciones (barrio, punto de referencia), opcional
 */
public record DomicilioVentaRequest(
        @NotBlank(message = "es obligatoria") @Size(max = 300, message = "admite hasta 300 caracteres") String direccion,
        @NotBlank(message = "es obligatorio") @Size(max = 30, message = "admite hasta 30 caracteres") String telefono,
        @Size(max = 300, message = "admite hasta 300 caracteres") String referencia) {
}
