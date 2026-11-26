package com.proyecto.supply_core.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.reporte.dto.VentasTurnoReporte;
import com.proyecto.supply_core.reporte.service.CajaReporteService.BaseTurno;
import com.proyecto.supply_core.reporte.service.CajaReporteService.SumaLibro;

/** Ventas por turno (spec 023): Java arma las cifras con las mismas sumas del cuadre del turno. */
class VentasPorTurnoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 25);
    private static final LocalDateTime ABRIO = HOY.atTime(8, 0);

    /**
     * @param turno turno
     * @param tipo  tipo
     * @param medio medio
     * @param monto suma con signo
     * @return suma del libro
     */
    private static SumaLibro suma(long turno, String tipo, String medio, String monto) {
        return new SumaLibro(turno, tipo, medio, new BigDecimal(monto));
    }

    /** Un turno cerrado: ventas netas por medio, devoluciones, ingresos, egresos y esperado igual al del cuadre. */
    @Test
    void armaLasCifrasDeUnTurnoCerrado() {
        BaseTurno cerrado = new BaseTurno(1L, "Caja 1", "Ana", "CERRADO", ABRIO, ABRIO.plusHours(8),
                new BigDecimal("-500"));
        List<SumaLibro> sumas = List.of(
                suma(1, "BASE", "EFECTIVO", "50000"),
                suma(1, "VENTA", "EFECTIVO", "120000"),
                suma(1, "VENTA", "TARJETA_DEBITO", "80000"),
                suma(1, "ANULACION", "EFECTIVO", "-20000"),
                suma(1, "DEVOLUCION", "EFECTIVO", "-10000"),
                suma(1, "ABONO_CARTERA", "TRANSFERENCIA", "30000"),
                suma(1, "INGRESO", "EFECTIVO", "5000"),
                suma(1, "GASTO", "EFECTIVO", "-7000"),
                suma(1, "RETIRO", "EFECTIVO", "-100000"));

        VentasTurnoReporte r = CajaReporteService.ventasPorTurno(HOY, HOY, List.of(cerrado), sumas);

        VentasTurnoReporte.Turno t = r.turnos().getFirst();
        assertThat(t.ventas()).containsOnlyKeys("EFECTIVO", "TARJETA_DEBITO");
        assertThat(t.ventas().get("EFECTIVO")).isEqualByComparingTo("100000");
        assertThat(t.ventas().keySet()).containsExactly("EFECTIVO", "TARJETA_DEBITO");
        assertThat(t.totalVentas()).isEqualByComparingTo("180000");
        assertThat(t.anulaciones()).isEqualByComparingTo("20000");
        assertThat(t.devoluciones()).isEqualByComparingTo("10000");
        assertThat(t.abonos()).isEqualByComparingTo("30000");
        assertThat(t.ingresos()).isEqualByComparingTo("5000");
        assertThat(t.gastos()).isEqualByComparingTo("7000");
        assertThat(t.retiros()).isEqualByComparingTo("100000");
        // Lo mismo que CalculoCaja.esperado: la suma de todos los movimientos, con la base.
        assertThat(t.esperado()).isEqualByComparingTo("148000");
        assertThat(t.contado()).isEqualByComparingTo("147500");
        assertThat(t.diferencia()).isEqualByComparingTo("-500");
    }

    /** Un turno abierto no tiene contado ni diferencia y no suma diferencia al periodo. */
    @Test
    void elTurnoAbiertoNoTieneContadoNiDiferencia() {
        BaseTurno abierto = new BaseTurno(2L, "Caja 2", "Luis", "ABIERTO", ABRIO, null, null);
        BaseTurno cerrado = new BaseTurno(1L, "Caja 1", "Ana", "APROBADO", ABRIO, ABRIO.plusHours(8),
                new BigDecimal("3000"));
        List<SumaLibro> sumas = List.of(suma(2, "VENTA", "BILLETERA", "15000"), suma(1, "VENTA", "EFECTIVO", "40000"),
                suma(1, "VENTA", "BILLETERA", "5000"));

        VentasTurnoReporte r = CajaReporteService.ventasPorTurno(HOY, HOY, List.of(abierto, cerrado), sumas);

        assertThat(r.turnos().getFirst().contado()).isNull();
        assertThat(r.turnos().getFirst().diferencia()).isNull();
        assertThat(r.totales().turnos()).isEqualTo(2);
        assertThat(r.totales().diferencia()).isEqualByComparingTo("3000");
        assertThat(r.totales().totalVentas()).isEqualByComparingTo("60000");
        assertThat(r.totales().ventas().keySet()).containsExactly("EFECTIVO", "BILLETERA");
        assertThat(r.totales().ventas().get("BILLETERA")).isEqualByComparingTo("20000");
    }

    /** Spec 029: el ajuste de pago pasa la venta de un medio a otro sin cambiar el total ni el esperado. */
    @Test
    void elAjusteDePagoMueveLaVentaDeMedioSinCambiarElTotal() {
        BaseTurno abierto = new BaseTurno(4L, "Caja 1", "Ana", "ABIERTO", ABRIO, null, null);
        List<SumaLibro> sumas = List.of(suma(4, "BASE", "EFECTIVO", "50000"), suma(4, "VENTA", "EFECTIVO", "20000"),
                suma(4, "AJUSTE_PAGO", "EFECTIVO", "-20000"), suma(4, "AJUSTE_PAGO", "TARJETA_DEBITO", "20000"));

        VentasTurnoReporte.Turno t = CajaReporteService.ventasPorTurno(HOY, HOY, List.of(abierto), sumas).turnos().getFirst();

        assertThat(t.ventas().get("EFECTIVO")).isEqualByComparingTo("0");
        assertThat(t.ventas().get("TARJETA_DEBITO")).isEqualByComparingTo("20000");
        assertThat(t.totalVentas()).isEqualByComparingTo("20000");
        assertThat(t.esperado()).isEqualByComparingTo("70000");
    }

    /** Un turno sin movimientos sale en cero. */
    @Test
    void unTurnoSinMovimientosSaleEnCero() {
        BaseTurno vacio = new BaseTurno(3L, "Caja 1", "Ana", "CERRADO", ABRIO, ABRIO.plusHours(1), BigDecimal.ZERO);

        VentasTurnoReporte r = CajaReporteService.ventasPorTurno(HOY, HOY, List.of(vacio), List.of());

        assertThat(r.turnos().getFirst().ventas()).isEmpty();
        assertThat(r.turnos().getFirst().esperado()).isEqualByComparingTo("0");
        assertThat(r.turnos().getFirst().contado()).isEqualByComparingTo("0");
    }
}
