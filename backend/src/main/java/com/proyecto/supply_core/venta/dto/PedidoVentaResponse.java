package com.proyecto.supply_core.venta.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.venta.enums.EstadoPedidoVenta;

/**
 * Pedido de venta. No trae montos: el POS los pide al servidor con los precios del día.
 *
 * @param id          id
 * @param consecutivo consecutivo ({@code PV-000001})
 * @param estado      estado
 * @param clienteId   cliente
 * @param cliente     nombre del cliente
 * @param vendedor    nombre del vendedor
 * @param nota        nota para el cajero
 * @param ventaId     venta con que se cobró
 * @param creadoEn    fecha
 * @param lineas      líneas
 * @param descartadoPor  nombre de quien lo descartó ({@code null} si no se descartó o fue antes de la spec 016)
 * @param descartadoEn   cuándo se descartó
 * @param motivoDescarte por qué se descartó
 */
public record PedidoVentaResponse(Long id, String consecutivo, EstadoPedidoVenta estado, Long clienteId,
        String cliente, String vendedor, String nota, Long ventaId, LocalDateTime creadoEn,
        List<PedidoVentaLineaResponse> lineas, String descartadoPor, LocalDateTime descartadoEn, String motivoDescarte) {
}
