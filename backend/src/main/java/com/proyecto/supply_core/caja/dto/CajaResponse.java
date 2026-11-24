package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.caja.entity.Caja;

/**
 * Una caja.
 *
 * @param id                 caja
 * @param nombre             nombre
 * @param sedeId             sede
 * @param sede               nombre de la sede
 * @param bodegaId           bodega de la que descuenta
 * @param bodega             nombre de la bodega
 * @param prefijoConsecutivo prefijo de los consecutivos
 * @param topeEfectivo       tope de efectivo
 * @param activa             si se puede abrir turno en ella
 * @param turnoAbierto       si tiene un turno abierto ahora
 * @param turnoAbiertoPor    nombre de quien tiene el turno abierto ({@code null} si la caja está libre); sin montos
 * @param turnoAbiertoDesde  cuándo se abrió ese turno ({@code null} si la caja está libre)
 */
public record CajaResponse(Long id, String nombre, Long sedeId, String sede, Long bodegaId, String bodega,
        String prefijoConsecutivo, BigDecimal topeEfectivo, boolean activa, boolean turnoAbierto,
        String turnoAbiertoPor, LocalDateTime turnoAbiertoDesde) {

    /**
     * @param c            caja con su sede y su bodega
     * @param turnoAbierto si tiene un turno abierto
     * @return respuesta
     */
    public static CajaResponse of(Caja c, boolean turnoAbierto) {
        return new CajaResponse(c.getId(), c.getNombre(), c.getSede().getId(), c.getSede().getNombre(),
                c.getBodega().getId(), c.getBodega().getNombre(), c.getPrefijoConsecutivo(), c.getTopeEfectivo(),
                c.isActiva(), turnoAbierto, null, null);
    }

    /**
     * Caja con el turno abierto que la ocupa (spec 015): quién lo tiene y desde cuándo, nunca sus montos.
     *
     * @param c         caja con su sede y su bodega
     * @param cajero    nombre de quien tiene el turno abierto
     * @param desde     apertura del turno
     * @return respuesta con la caja ocupada
     */
    public static CajaResponse ocupada(Caja c, String cajero, LocalDateTime desde) {
        return new CajaResponse(c.getId(), c.getNombre(), c.getSede().getId(), c.getSede().getNombre(),
                c.getBodega().getId(), c.getBodega().getNombre(), c.getPrefijoConsecutivo(), c.getTopeEfectivo(),
                c.isActiva(), true, cajero, desde);
    }
}
