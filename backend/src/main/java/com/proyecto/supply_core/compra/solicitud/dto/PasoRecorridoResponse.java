package com.proyecto.supply_core.compra.solicitud.dto;

import java.time.LocalDateTime;

/**
 * Un paso en el recorrido de una solicitud de compra (spec 033): desde que se crea hasta la factura.
 *
 * @param fecha   cuándo pasó
 * @param tipo    SOLICITUD, APROBACION, RFQ, COTIZACION, ADJUDICACION, ORDEN, RECEPCION o FACTURA
 * @param titulo  qué pasó, en pocas palabras
 * @param detalle quién, cuánto o en qué estado quedó ({@code null} si no hay más que decir)
 */
public record PasoRecorridoResponse(LocalDateTime fecha, String tipo, String titulo, String detalle) {
}
