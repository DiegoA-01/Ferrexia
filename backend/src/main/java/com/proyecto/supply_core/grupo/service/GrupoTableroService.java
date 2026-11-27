package com.proyecto.supply_core.grupo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.EmpresaEntrableResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.TableroEmpresaResponse;
import com.proyecto.supply_core.grupo.dto.GrupoDtos.TableroGrupoResponse;
import com.proyecto.supply_core.reporte.enums.AgrupacionVenta;
import com.proyecto.supply_core.reporte.service.ReporteService;
import com.proyecto.supply_core.reporte.service.VentaReporteService;

/**
 * Tablero del grupo (spec 035): ventas netas, compras e inventario valorizado de cada empresa del grupo y el total.
 * Cada empresa se lee dentro de su propio contexto ({@code EmpresaContexto.como}) con los mismos cálculos de sus
 * reportes, así que lo que suma el grupo es exactamente lo que ve cada empresa. Java suma y redondea (pesos, dos
 * decimales); nada se calcula en el frontend.
 */
@Service
public class GrupoTableroService {

    private static final int ESCALA = 2;

    private final GrupoEmpresaService empresaService;
    private final VentaReporteService ventaReporte;
    private final ReporteService reporteService;

    /**
     * @param empresaService    empresas del grupo con vínculo activo y suscripción que permite el acceso
     * @param ventaReporte      ventas netas de una empresa
     * @param reporteService    compras e inventario valorizado de una empresa
     */
    public GrupoTableroService(GrupoEmpresaService empresaService, VentaReporteService ventaReporte,
            ReporteService reporteService) {
        this.empresaService = empresaService;
        this.ventaReporte = ventaReporte;
        this.reporteService = reporteService;
    }

    /**
     * Tablero del grupo del token para un periodo, solo con las empresas a las que el super administrador puede entrar.
     *
     * @param desde primer día (vacío = primer día del mes en curso)
     * @param hasta último día (vacío = hoy)
     * @return cifras por empresa y total del grupo
     * @throws ParametroInvalidoException si el periodo está al revés o es demasiado largo
     */
    public TableroGrupoResponse tablero(LocalDate desde, LocalDate hasta) {
        LocalDate[] periodo = VentaReporteService.periodo(desde, hasta);
        List<TableroEmpresaResponse> filas = new ArrayList<>();
        for (EmpresaEntrableResponse e : empresaService.empresas()) {
            filas.add(EmpresaContexto.como(e.id(), () -> fila(e, periodo[0], periodo[1])));
        }
        BigDecimal ventas = BigDecimal.ZERO;
        BigDecimal compras = BigDecimal.ZERO;
        BigDecimal inventario = BigDecimal.ZERO;
        for (TableroEmpresaResponse f : filas) {
            ventas = ventas.add(f.ventasNetas());
            compras = compras.add(f.compras());
            inventario = inventario.add(f.inventarioValorizado());
        }
        return new TableroGrupoResponse(periodo[0], periodo[1], filas,
                new TableroEmpresaResponse(null, null, dos(ventas), dos(compras), dos(inventario)));
    }

    /**
     * Cifras de una empresa; debe correr dentro de su contexto.
     *
     * @param e     empresa
     * @param desde primer día
     * @param hasta último día
     * @return fila de la empresa
     */
    private TableroEmpresaResponse fila(EmpresaEntrableResponse e, LocalDate desde, LocalDate hasta) {
        BigDecimal ventas = ventaReporte.deEmpresa(desde, hasta, AgrupacionVenta.DIA).totales().neto();
        BigDecimal compras = reporteService.gasto(desde, hasta, null, null).total();
        BigDecimal inventario = reporteService.inventario(null, false).valorTotal();
        return new TableroEmpresaResponse(e.id(), e.razonSocial(), dos(ventas), dos(compras), dos(inventario));
    }

    /**
     * @param valor monto o {@code null}
     * @return el monto con dos decimales (cero si no hay)
     */
    private static BigDecimal dos(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(ESCALA, RoundingMode.HALF_UP);
    }
}
