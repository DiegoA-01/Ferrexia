package com.proyecto.supply_core.compra.enlace.dto;

import java.time.LocalDateTime;

/**
 * Enlace de cotización de un proveedor invitado, visto por compras (spec 031).
 *
 * @param proveedorId id del proveedor
 * @param proveedor   razón social
 * @param correo      correo del proveedor (si tiene, el enlace se le envía)
 * @param telefono    teléfono del proveedor (para WhatsApp)
 * @param url         enlace completo para copiar
 * @param estado      PENDIENTE, USADO o VENCIDO
 * @param venceEn     vencimiento (fecha límite de la RFQ)
 * @param usadoEn     cuándo cotizó desde el enlace
 */
public record EnlaceResponse(Long proveedorId, String proveedor, String correo, String telefono, String url,
        String estado, LocalDateTime venceEn, LocalDateTime usadoEn) {
}
