package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.proyecto.supply_core.caja.enums.MedioPago;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

/**
 * Cierre ciego: lo que el cajero contó, sin haber visto lo esperado. El efectivo llega como total escrito a mano, como
 * conteo por billete y moneda (spec 023) o de las dos formas; en ese caso deben coincidir.
 *
 * @param contadoEfectivo efectivo contado; puede faltar si viene el conteo
 * @param contadoOtros    total de vouchers y transferencias por medio (sin el efectivo); medios ausentes = 0
 * @param observacion     nota del cajero, opcional
 * @param conteo          billetes y monedas contados, opcional (Java suma el efectivo)
 */
public record CerrarTurnoRequest(
        @DecimalMin(value = "0", message = "no puede ser negativo")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal contadoEfectivo,
        Map<MedioPago, BigDecimal> contadoOtros,
        @Size(max = 500, message = "máximo 500 caracteres") String observacion,
        @Size(max = 12, message = "máximo una fila por denominación") List<@Valid ConteoDenominacion> conteo) {

    /**
     * Cierre escrito a mano, como antes de la spec 023.
     *
     * @param contadoEfectivo efectivo contado
     * @param contadoOtros    otros medios
     * @param observacion     nota
     */
    public CerrarTurnoRequest(BigDecimal contadoEfectivo, Map<MedioPago, BigDecimal> contadoOtros, String observacion) {
        this(contadoEfectivo, contadoOtros, observacion, null);
    }
}
