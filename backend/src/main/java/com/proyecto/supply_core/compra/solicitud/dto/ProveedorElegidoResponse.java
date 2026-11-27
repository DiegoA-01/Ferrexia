package com.proyecto.supply_core.compra.solicitud.dto;

import com.proyecto.supply_core.proveedor.entity.Proveedor;

/**
 * Proveedor que se puede elegir o que ya se eligió para una solicitud (spec 033).
 *
 * @param id          id del proveedor
 * @param razonSocial nombre
 */
public record ProveedorElegidoResponse(Long id, String razonSocial) {

    /**
     * @param p proveedor
     * @return DTO
     */
    public static ProveedorElegidoResponse of(Proveedor p) {
        return new ProveedorElegidoResponse(p.getId(), p.getRazonSocial());
    }
}
