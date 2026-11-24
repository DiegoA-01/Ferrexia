package com.proyecto.supply_core.exportacion.generador;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Texto de las celdas del PDF de tablas con formato colombiano (spec 017). */
class TablaPdfGeneradorTest {

    /** Los montos llevan punto de miles y coma decimal. */
    @Test
    void losMontosLlevanPuntoDeMiles() {
        assertThat(TablaPdfGenerador.texto(new BigDecimal("1250000.50"))).isEqualTo("1.250.000,5");
        assertThat(TablaPdfGenerador.texto(new BigDecimal("1234"))).isEqualTo("1.234");
    }

    /** Las cantidades salen como número (antes salían como "Cantidad[valor=...]"), con hasta 4 decimales. */
    @Test
    void lasCantidadesSalenComoNumero() {
        assertThat(TablaPdfGenerador.texto(new ExcelGenerador.Cantidad(new BigDecimal("12.0000")))).isEqualTo("12");
        assertThat(TablaPdfGenerador.texto(new ExcelGenerador.Cantidad(new BigDecimal("1500.2500")))).isEqualTo("1.500,25");
    }

    /** Los conteos llevan punto de miles y nada vacío rompe la celda. */
    @Test
    void losConteosLlevanPuntoDeMilesYElVacioQuedaEnBlanco() {
        assertThat(TablaPdfGenerador.texto(1234L)).isEqualTo("1.234");
        assertThat(TablaPdfGenerador.texto(7)).isEqualTo("7");
        assertThat(TablaPdfGenerador.texto(null)).isEmpty();
        assertThat(TablaPdfGenerador.texto("Cemento")).isEqualTo("Cemento");
    }
}
