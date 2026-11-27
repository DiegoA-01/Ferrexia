package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;

/**
 * Un movimiento del libro de caja.
 *
 * @param id           movimiento
 * @param tipo         tipo
 * @param medioPago    medio
 * @param monto        positivo entra, negativo sale
 * @param motivo       motivo o {@code null}
 * @param soporte      soporte o {@code null}
 * @param usuario      quién lo registró
 * @param autorizadoPor quién lo autorizó o {@code null}
 * @param creadoEn     cuándo
 * @param categoria    nombre de la categoría del ingreso o egreso (spec 021) o {@code null}
 * @param referenciaTipo a qué pertenece (por ejemplo {@code VENTA}) o {@code null}
 * @param referenciaId id de aquello a lo que pertenece o {@code null} (con {@code VENTA} es el id de la venta)
 * @param consecutivo  consecutivo de la venta si el movimiento es de una venta, o {@code null}
 * @param totalVenta   total de esa venta (lo calcula el backend) o {@code null}
 */
public record MovimientoCajaResponse(Long id, TipoMovimientoCaja tipo, MedioPago medioPago, BigDecimal monto,
        String motivo, String soporte, String usuario, String autorizadoPor, LocalDateTime creadoEn,
        String categoria, String referenciaTipo, Long referenciaId, String consecutivo, BigDecimal totalVenta) {
}
