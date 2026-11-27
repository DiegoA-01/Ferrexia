package com.proyecto.supply_core.grupo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.EmpresaEntrableResponse;
import com.proyecto.supply_core.reporte.dto.GastoResponse;
import com.proyecto.supply_core.reporte.dto.InventarioResumen;
import com.proyecto.supply_core.reporte.dto.VentaReporte;
import com.proyecto.supply_core.reporte.enums.AgrupacionVenta;
import com.proyecto.supply_core.reporte.service.ReporteService;
import com.proyecto.supply_core.reporte.service.VentaReporteService;
import com.proyecto.supply_core.security.context.GrupoActual;
import com.proyecto.supply_core.security.context.GrupoAutenticado;

/** El tablero del grupo suma lo que cada empresa calcula con sus propios reportes, cada una en su contexto (spec 035). */
class GrupoTableroServiceTest {

    private final GrupoEmpresaService empresas = mock(GrupoEmpresaService.class);
    private final VentaReporteService ventas = mock(VentaReporteService.class);
    private final ReporteService reportes = mock(ReporteService.class);
    private final GrupoTableroService service = new GrupoTableroService(empresas, ventas, reportes);
    private final LocalDate desde = LocalDate.of(2026, 11, 1);
    private final LocalDate hasta = LocalDate.of(2026, 11, 27);

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GrupoActual.ATRIBUTO, new GrupoAutenticado(7L, 2L, "super@grupo.com", "Super"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(empresas.empresas()).thenReturn(List.of(new EmpresaEntrableResponse(10L, "A", "ACTIVA"),
                new EmpresaEntrableResponse(11L, "B", "ACTIVA")));
        cifras();
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** Configura lo que devuelven los reportes mientras el contexto es la empresa indicada. */
    private void cifras() {
        when(ventas.deEmpresa(eq(desde), eq(hasta), eq(AgrupacionVenta.DIA))).thenAnswer(i -> {
            BigDecimal valor = EmpresaContexto.requerida().equals(10L) ? new BigDecimal("1000.50") : new BigDecimal("250.75");
            return reporteConNeto(valor);
        });
        when(reportes.gasto(eq(desde), eq(hasta), any(), any())).thenAnswer(i -> gasto(
                EmpresaContexto.requerida().equals(10L) ? new BigDecimal("300.25") : new BigDecimal("100.10")));
        when(reportes.inventario(any(), eq(false))).thenAnswer(i -> new InventarioResumen(0, 0,
                EmpresaContexto.requerida().equals(10L) ? new BigDecimal("5000.00") : null, List.of()));
    }

    private static VentaReporte reporteConNeto(BigDecimal neto) {
        VentaReporte.Totales t = new VentaReporte.Totales(1, neto, BigDecimal.ZERO, BigDecimal.ZERO, neto, neto, null, null,
                null, null, neto);
        return new VentaReporte(null, null, AgrupacionVenta.DIA, List.of(), t, false);
    }

    private static GastoResponse gasto(BigDecimal total) {
        return new GastoResponse(null, null, 1, total, BigDecimal.ZERO, total, null, List.of(), List.of(), List.of());
    }

    @Test
    void elTotalDelGrupoEsLaSumaDeLasEmpresasCadaUnaLeidaEnSuContexto() {
        var t = service.tablero(desde, hasta);

        assertEquals(2, t.empresas().size());
        assertEquals(new BigDecimal("1000.50"), t.empresas().get(0).ventasNetas());
        assertEquals(new BigDecimal("300.25"), t.empresas().get(0).compras());
        assertEquals(new BigDecimal("5000.00"), t.empresas().get(0).inventarioValorizado());
        assertEquals(new BigDecimal("0.00"), t.empresas().get(1).inventarioValorizado()); // sin valor = cero
        assertEquals(new BigDecimal("1251.25"), t.total().ventasNetas());
        assertEquals(new BigDecimal("400.35"), t.total().compras());
        assertEquals(new BigDecimal("5000.00"), t.total().inventarioValorizado());
        assertEquals(desde, t.desde());
        assertEquals(hasta, t.hasta());
    }

    @Test
    void unPeriodoAlReveSeRechaza() {
        assertThrows(ParametroInvalidoException.class, () -> service.tablero(hasta, desde));
    }
}
