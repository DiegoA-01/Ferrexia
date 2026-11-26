package com.proyecto.supply_core.compra.enlace.dto;

import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionResponse;
import com.proyecto.supply_core.compra.rfq.dto.RfqPortalResponse;

/**
 * Lo que ve el proveedor al abrir su enlace (spec 031): solo la empresa que pide, su propio nombre, la RFQ sin datos
 * internos y su borrador.
 *
 * @param empresa             razón social de la empresa que pide la cotización
 * @param proveedor           razón social del proveedor invitado
 * @param rfq                 RFQ con sus productos (sin invitados ni datos de la solicitud)
 * @param cotizacion          borrador guardado, o {@code null} si aún no empieza
 * @param turnstileClaveSitio clave pública del widget anti-bot (vacía si no está configurado)
 */
public record CotizacionPublicaResponse(String empresa, String proveedor, RfqPortalResponse rfq,
        CotizacionResponse cotizacion, String turnstileClaveSitio) {
}
