package com.proyecto.supply_core.venta.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Cambio del método de pago de una venta (spec 029, D16). Los pagos nuevos suman exactamente el total de la venta.
 *
 * @param pagos        pagos que quedan vigentes (1 a 10, solo medios que entran a la caja)
 * @param motivo       por qué se cambia (el cajero marcó efectivo y era tarjeta...)
 * @param autorizacion permiso de un solo uso del supervisor (acción CAMBIAR_PAGO); solo si la sesión no tiene el
 *                     permiso de cambiar pagos
 */
public record CambiarPagoRequest(
        @NotEmpty(message = "agregue al menos un pago") @Size(max = 10, message = "admite hasta 10 pagos")
        List<@Valid PagoVentaRequest> pagos,
        @NotBlank(message = "es obligatorio") @Size(min = 5, max = 300, message = "debe tener entre 5 y 300 caracteres")
        String motivo,
        @Size(max = 100, message = "no es una autorización válida") String autorizacion) {
}
