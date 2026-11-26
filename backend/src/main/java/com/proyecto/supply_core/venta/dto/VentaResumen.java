package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.venta.enums.EstadoVenta;
import com.proyecto.supply_core.venta.enums.MedioPagoVenta;

/**
 * Venta en el historial.
 *
 * @param id           id
 * @param consecutivo  consecutivo
 * @param estado       estado
 * @param caja         nombre de la caja
 * @param turnoId      turno
 * @param cliente      nombre del cliente
 * @param cajero       nombre del cajero
 * @param vendedor     nombre del vendedor
 * @param totalAPagar  lo que pagó el cliente
 * @param confirmadaEn fecha y hora
 * @param medios       medios de pago usados, sin repetir (spec 021)
 * @param tipoServicio MOSTRADOR o DOMICILIO (spec 032)
 */
public record VentaResumen(Long id, String consecutivo, EstadoVenta estado, String caja, Long turnoId, String cliente,
        String cajero, String vendedor, BigDecimal totalAPagar, LocalDateTime confirmadaEn,
        List<MedioPagoVenta> medios, String tipoServicio) {
}
