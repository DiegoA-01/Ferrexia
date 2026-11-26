package com.proyecto.supply_core.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.inventario.dto.ProductoRequest;
import com.proyecto.supply_core.inventario.dto.ProductoUpdateRequest;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.MovimientoInventarioRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.venta.entity.TarifaImpuesto;
import com.proyecto.supply_core.venta.repository.TarifaImpuestoRepository;

/** Spec 026: el producto se crea completo, con impuesto, proveedor y fecha del costo, y avisa si supera el máximo. */
class ProductoCompletoTest {

    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final CategoriaProductoRepository categorias = mock(CategoriaProductoRepository.class);
    private final UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
    private final StockProductoBodegaRepository stock = mock(StockProductoBodegaRepository.class);
    private final MovimientoInventarioRepository movimientos = mock(MovimientoInventarioRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final TarifaImpuestoRepository tarifas = mock(TarifaImpuestoRepository.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final ProductoService servicio = new ProductoService(productos, categorias, unidades, stock, movimientos,
            auditoria, tarifas, proveedores);

    private TarifaImpuesto iva5;
    private Proveedor electrosur;

    @BeforeEach
    void setUp() {
        CategoriaProducto cat = new CategoriaProducto();
        cat.setId(1L);
        cat.setNombre("Eléctrico");
        UnidadMedida und = new UnidadMedida();
        und.setId(1L);
        und.setCodigo("UND");
        und.setNombre("Unidad");
        und.setMagnitud(com.proyecto.supply_core.catalogo.enums.Magnitud.CANTIDAD);
        und.setFactorConversionBase(BigDecimal.ONE);
        when(categorias.findById(1L)).thenReturn(Optional.of(cat));
        when(unidades.findById(1L)).thenReturn(Optional.of(und));
        iva5 = new TarifaImpuesto();
        iva5.setId(5L);
        iva5.setCodigo("IVA5");
        iva5.setNombre("IVA 5 %");
        iva5.setPorcentaje(new BigDecimal("5"));
        when(tarifas.findById(5L)).thenReturn(Optional.of(iva5));
        electrosur = new Proveedor();
        electrosur.setId(7L);
        electrosur.setRazonSocial("Electrosur");
        when(proveedores.findById(7L)).thenReturn(Optional.of(electrosur));
        when(productos.save(any())).thenAnswer(i -> {
            Producto p = i.getArgument(0);
            p.setId(20L);
            return p;
        });
    }

    /**
     * @param tarifa    impuesto
     * @param proveedor proveedor del costo
     * @param fecha     fecha del costo
     * @param costo     costo de referencia
     * @return alta con los datos de la spec 026
     */
    private static ProductoRequest alta(Long tarifa, Long proveedor, LocalDate fecha, String costo) {
        return new ProductoRequest("BRK-30", "Breaker 30 A", null, 1L, 1L, new BigDecimal("2"), new BigDecimal("10"),
                costo == null ? null : new BigDecimal(costo), tarifa, proveedor, fecha);
    }

    @Test
    void seCreaConSuImpuestoSuProveedorYLaFechaDelCosto() {
        LocalDate fecha = LocalDate.now().minusDays(3);

        var r = servicio.crear(alta(5L, 7L, fecha, "25000"));

        assertEquals(5L, r.tarifaImpuestoId());
        assertEquals("IVA 5 %", r.tarifaImpuesto());
        assertEquals(7L, r.proveedorReferenciaId());
        assertEquals("Electrosur", r.proveedorReferencia());
        assertEquals(fecha, r.costoReferenciaFecha());
        verify(tarifas, never()).findByCodigo(any());
    }

    @Test
    void conCostoYSinFechaLaFechaEsHoy() {
        var r = servicio.crear(alta(5L, null, null, "25000"));

        assertEquals(LocalDate.now(), r.costoReferenciaFecha());
        assertNull(r.proveedorReferenciaId());
    }

    @Test
    void sinImpuestoElegidoQuedaElIvaDelDiecinueveComoAntes() {
        TarifaImpuesto iva19 = new TarifaImpuesto();
        iva19.setId(1L);
        iva19.setCodigo("IVA19");
        iva19.setNombre("IVA 19 %");
        when(tarifas.findByCodigo("IVA19")).thenReturn(Optional.of(iva19));

        var r = servicio.crear(alta(null, null, null, null));

        assertEquals(1L, r.tarifaImpuestoId());
        assertNull(r.costoReferenciaFecha());
    }

    @Test
    void unImpuestoInactivoOAjenoYUnProveedorAjenoSeRechazan() {
        iva5.setActivo(false);
        assertEquals("TARIFA_INVALIDA", assertThrows(NegocioException.class,
                () -> servicio.crear(alta(5L, null, null, null))).getCodigo());
        // de otra empresa: @TenantId no la encuentra
        assertEquals("TARIFA_INVALIDA", assertThrows(NegocioException.class,
                () -> servicio.crear(alta(99L, null, null, null))).getCodigo());
        assertEquals("PROVEEDOR_INVALIDO", assertThrows(NegocioException.class,
                () -> servicio.crear(alta(null, 99L, null, null))).getCodigo());
        verify(productos, never()).save(any());
    }

    @Test
    void editarSinLosDatosNuevosNoBorraElProveedorNiLaFecha() {
        Producto p = new Producto();
        p.setId(20L);
        p.setCodigo("BRK-30");
        p.setNombre("Breaker");
        p.setCategoria(categorias.findById(1L).orElseThrow());
        p.setUnidadMedida(unidades.findById(1L).orElseThrow());
        p.setProveedorReferencia(electrosur);
        p.setCostoReferenciaFecha(LocalDate.of(2026, 11, 1));
        when(productos.findWithRelacionesById(20L)).thenReturn(Optional.of(p));
        when(stock.totalDeProducto(20L)).thenReturn(new BigDecimal("12.0000"));

        var r = servicio.actualizar(20L, new ProductoUpdateRequest("BRK-30", "Breaker 30 A", null, 1L, 1L,
                new BigDecimal("2"), new BigDecimal("10"), new BigDecimal("26000")));

        assertEquals(7L, r.proveedorReferenciaId());
        assertEquals(LocalDate.of(2026, 11, 1), r.costoReferenciaFecha());
        // D17: 12 sobre un máximo de 10 se permite y se marca
        assertTrue(r.sobreStockMaximo());
    }

    @Test
    void laListaDeSobreElMaximoTraeSusTotales() {
        Producto p = new Producto();
        p.setId(30L);
        p.setCodigo("CAB-12");
        p.setNombre("Cable");
        p.setCategoria(categorias.findById(1L).orElseThrow());
        p.setUnidadMedida(unidades.findById(1L).orElseThrow());
        p.setStockMaximo(new BigDecimal("100"));
        when(productos.sobreStockMaximo()).thenReturn(List.of(p));
        when(stock.totalesDe(List.of(30L))).thenReturn(List.of());

        var lista = servicio.sobreStockMaximo();

        assertEquals(1, lista.size());
        assertFalse(lista.get(0).sobreStockMaximo()); // sin fila de stock el total es 0: la marca la da el total real
    }
}
