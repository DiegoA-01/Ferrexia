package com.proyecto.supply_core.venta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Descarte de un pedido de venta sin cobrar (spec 016): queda en "Eliminados" con quién, cuándo y este motivo.
 *
 * @param motivo por qué se descarta (el cliente desistió, pedido repetido...)
 */
public record DescartarPedidoRequest(
        @NotBlank(message = "es obligatorio") @Size(min = 3, max = 300, message = "debe tener entre 3 y 300 caracteres") String motivo) {
}
