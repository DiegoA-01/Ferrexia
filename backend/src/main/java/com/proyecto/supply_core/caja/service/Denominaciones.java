package com.proyecto.supply_core.caja.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.proyecto.supply_core.caja.dto.ConteoDenominacion;
import com.proyecto.supply_core.caja.enums.TipoDenominacion;
import com.proyecto.supply_core.common.exception.NegocioException;

/**
 * Billetes y monedas colombianos vigentes y la suma del conteo del cierre (spec 023), en Java y sin base de datos.
 */
public final class Denominaciones {

    /**
     * Una denominación aceptada.
     *
     * @param tipo  billete o moneda
     * @param valor valor en pesos
     */
    public record Denominacion(TipoDenominacion tipo, int valor) {
    }

    /** Billetes vigentes, de mayor a menor. */
    public static final List<Integer> BILLETES = List.of(100_000, 50_000, 20_000, 10_000, 5_000, 2_000, 1_000);

    /** Monedas vigentes, de mayor a menor. */
    public static final List<Integer> MONEDAS = List.of(1_000, 500, 200, 100, 50);

    /** No se instancia: solo métodos estáticos. */
    private Denominaciones() {
    }

    /**
     * @return todas las denominaciones aceptadas: primero los billetes y luego las monedas
     */
    public static List<Denominacion> todas() {
        List<Denominacion> lista = new ArrayList<>();
        BILLETES.forEach(v -> lista.add(new Denominacion(TipoDenominacion.BILLETE, v)));
        MONEDAS.forEach(v -> lista.add(new Denominacion(TipoDenominacion.MONEDA, v)));
        return lista;
    }

    /**
     * @param tipo  billete o moneda
     * @param valor valor en pesos
     * @return {@code true} si es un billete o una moneda vigente
     */
    public static boolean existe(TipoDenominacion tipo, Integer valor) {
        if (tipo == null || valor == null) {
            return false;
        }
        return (tipo == TipoDenominacion.BILLETE ? BILLETES : MONEDAS).contains(valor);
    }

    /**
     * Valida el conteo y suma valor × cantidad de cada fila.
     *
     * @param conteo filas del conteo
     * @return efectivo contado con 2 decimales
     * @throws NegocioException {@code DENOMINACION_INVALIDA} si una fila no es un billete o una moneda vigente, o
     *                          {@code DENOMINACION_REPETIDA} si una denominación sale dos veces
     */
    public static BigDecimal total(List<ConteoDenominacion> conteo) {
        Set<Denominacion> vistas = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ConteoDenominacion fila : conteo) {
            if (!existe(fila.tipo(), fila.valor())) {
                throw new NegocioException("DENOMINACION_INVALIDA", "No existe "
                        + (fila.tipo() == TipoDenominacion.MONEDA ? "moneda" : "billete") + " de $ " + fila.valor());
            }
            if (!vistas.add(new Denominacion(fila.tipo(), fila.valor()))) {
                throw new NegocioException("DENOMINACION_REPETIDA", "La denominación de $ " + fila.valor()
                        + " está dos veces en el conteo");
            }
            int cantidad = fila.cantidad() == null ? 0 : fila.cantidad();
            total = total.add(BigDecimal.valueOf((long) fila.valor() * cantidad));
        }
        return total.setScale(2);
    }
}
