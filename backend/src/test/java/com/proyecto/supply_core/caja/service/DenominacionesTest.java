package com.proyecto.supply_core.caja.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.caja.dto.ConteoDenominacion;
import com.proyecto.supply_core.caja.enums.TipoDenominacion;
import com.proyecto.supply_core.common.exception.NegocioException;

/** Billetes y monedas vigentes y la suma del conteo del cierre (spec 023). */
class DenominacionesTest {

    /**
     * @param tipo     billete o moneda
     * @param valor    valor
     * @param cantidad unidades
     * @return fila del conteo
     */
    private static ConteoDenominacion fila(TipoDenominacion tipo, int valor, int cantidad) {
        return new ConteoDenominacion(tipo, valor, cantidad, null);
    }

    /** 3 × 50.000 + 2 × 1.000 (billete) + 4 × 1.000 (moneda) = 156.000. */
    @Test
    void sumaValorPorCantidadConElMilComoBilleteYComoMoneda() {
        BigDecimal total = Denominaciones.total(List.of(fila(TipoDenominacion.BILLETE, 50_000, 3),
                fila(TipoDenominacion.BILLETE, 1_000, 2), fila(TipoDenominacion.MONEDA, 1_000, 4)));

        assertThat(total).isEqualByComparingTo("156000");
        assertThat(total.scale()).isEqualTo(2);
    }

    /** Las filas en cero no suman y el conteo vacío da 0. */
    @Test
    void lasFilasEnCeroNoSuman() {
        assertThat(Denominaciones.total(List.of(fila(TipoDenominacion.BILLETE, 100_000, 0)))).isEqualByComparingTo("0");
        assertThat(Denominaciones.total(List.of())).isEqualByComparingTo("0");
    }

    /** Doce denominaciones: siete billetes (de 100.000 a 1.000) y cinco monedas (de 1.000 a 50). */
    @Test
    void lasDenominacionesVigentesSonDoce() {
        assertThat(Denominaciones.todas()).hasSize(12);
        assertThat(Denominaciones.existe(TipoDenominacion.BILLETE, 2_000)).isTrue();
        assertThat(Denominaciones.existe(TipoDenominacion.MONEDA, 2_000)).isFalse();
        assertThat(Denominaciones.existe(TipoDenominacion.MONEDA, 50)).isTrue();
        assertThat(Denominaciones.existe(null, 50)).isFalse();
    }

    /** Un billete que no existe se rechaza. */
    @Test
    void unaDenominacionInventadaSeRechaza() {
        NegocioException e = catchThrowableOfType(NegocioException.class,
                () -> Denominaciones.total(List.of(fila(TipoDenominacion.BILLETE, 30_000, 1))));

        assertThat(e).isNotNull();
        assertThat(e.getCodigo()).isEqualTo("DENOMINACION_INVALIDA");
    }

    /** La misma denominación dos veces se rechaza (se contaría doble). */
    @Test
    void unaDenominacionRepetidaSeRechaza() {
        NegocioException e = catchThrowableOfType(NegocioException.class,
                () -> Denominaciones.total(List.of(fila(TipoDenominacion.MONEDA, 500, 1),
                        fila(TipoDenominacion.MONEDA, 500, 2))));

        assertThat(e).isNotNull();
        assertThat(e.getCodigo()).isEqualTo("DENOMINACION_REPETIDA");
    }
}
