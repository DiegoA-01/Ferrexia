package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.enums.MedioPago;

/**
 * Lo que debería haber en un turno en este momento (spec 021, decisión D20). Solo lo ve quien supervisa: el cajero
 * sigue cerrando a ciegas.
 *
 * @param turnoId  turno
 * @param estado   estado del turno
 * @param esperado lo esperado por medio de pago (Java lo calcula del libro de caja)
 * @param total    suma de lo esperado
 */
public record EsperadoTurnoResponse(Long turnoId, EstadoTurno estado, Map<MedioPago, BigDecimal> esperado,
        BigDecimal total) {
}
