package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ventas por turno de un periodo (spec 023): lo que entró y salió de cada turno según su libro de caja. Lo calcula Java
 * con las mismas sumas del cuadre (spec 003), así que el esperado coincide con el del cierre.
 *
 * @param desde   primer día (incluido)
 * @param hasta   último día (incluido)
 * @param turnos  turnos abiertos en el periodo, del más reciente al más antiguo
 * @param totales sumas del periodo
 */
public record VentasTurnoReporte(LocalDate desde, LocalDate hasta, List<Turno> turnos, Totales totales) {

    /**
     * @param id           turno
     * @param caja         caja
     * @param cajero       cajero
     * @param estado       ABIERTO, CERRADO o APROBADO
     * @param abiertoEn    apertura
     * @param cerradoEn    cierre ({@code null} si sigue abierto)
     * @param ventas       ventas netas de anulaciones por medio de pago (solo los medios con movimiento)
     * @param totalVentas  suma de las ventas netas
     * @param anulaciones  ventas anuladas dentro del turno (positivo)
     * @param devoluciones dinero devuelto a clientes (positivo)
     * @param abonos       abonos de clientes a su cartera
     * @param ingresos     ingresos de efectivo que no son venta
     * @param gastos       gastos pagados con la caja (positivo)
     * @param retiros      efectivo enviado a la bóveda (positivo)
     * @param esperado     lo que debería haber en todos los medios (incluye la base)
     * @param contado      lo que se contó al cerrar ({@code null} si sigue abierto)
     * @param diferencia   contado − esperado ({@code null} si sigue abierto)
     */
    @Schema(name = "VentasTurno")
    public record Turno(Long id, String caja, String cajero, String estado, LocalDateTime abiertoEn,
            LocalDateTime cerradoEn, Map<String, BigDecimal> ventas, BigDecimal totalVentas, BigDecimal anulaciones,
            BigDecimal devoluciones, BigDecimal abonos, BigDecimal ingresos, BigDecimal gastos, BigDecimal retiros,
            BigDecimal esperado, BigDecimal contado, BigDecimal diferencia) {
    }

    /**
     * @param turnos       turnos del periodo
     * @param ventas       ventas netas por medio de pago
     * @param totalVentas  suma de las ventas netas
     * @param devoluciones suma de las devoluciones
     * @param ingresos     suma de los ingresos y abonos
     * @param egresos      suma de los gastos y retiros
     * @param diferencia   suma de las diferencias de los turnos cerrados
     */
    @Schema(name = "VentasTurnoTotales")
    public record Totales(long turnos, Map<String, BigDecimal> ventas, BigDecimal totalVentas, BigDecimal devoluciones,
            BigDecimal ingresos, BigDecimal egresos, BigDecimal diferencia) {
    }
}
