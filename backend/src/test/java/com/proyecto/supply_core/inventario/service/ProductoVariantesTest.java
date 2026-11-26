package com.proyecto.supply_core.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.inventario.dto.ProductoUpdateRequest;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.MovimientoInventarioRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.venta.entity.TarifaImpuesto;
import com.proyecto.supply_core.venta.repository.TarifaImpuestoRepository;

/** Spec 028: el producto general propaga lo que heredan sus variantes y no entra a ninguna operación. */
class ProductoVariantesTest {

    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final CategoriaProductoRepository categorias = mock(CategoriaProductoRepository.class);
    private final UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
    private final StockProductoBodegaRepository stock = mock(StockProductoBodegaRepository.class);
    private final MovimientoInventarioRepository movimientos = mock(MovimientoInventarioRepository.class);
    private final TarifaImpuestoRepository tarifas = mock(TarifaImpuestoRepository.class);
    private final ProductoService servicio = new ProductoService(productos, categorias, unidades, stock, movimientos,
            mock(AuditoriaService.class), tarifas, null);

    private CategoriaProducto tornilleria;
    private CategoriaProducto ferreteria;
    private UnidadMedida und;
    private UnidadMedida caja;
    private TarifaImpuesto iva5;
    private Producto general;
    private Producto variante;

    @BeforeEach
    void setUp() {
        tornilleria = categoria(1L);
        ferreteria = categoria(2L);
        und = unidad(1L, "UND");
        caja = unidad(2L, "CAJA");
        iva5 = new TarifaImpuesto();
        iva5.setId(5L);
        iva5.setCodigo("IVA5");
        iva5.setActivo(true);
        when(tarifas.findById(5L)).thenReturn(Optional.of(iva5));

        general = new Producto();
        general.setId(50L);
        general.setCodigo("TD");
        general.setNombre("Tornillo drywall");
        general.setEsAgrupador(true);
        general.setVendible(false);
        general.setCategoria(tornilleria);
        general.setUnidadMedida(und);
        general.setStockMinimo(BigDecimal.ZERO);
        variante = new Producto();
        variante.setId(51L);
        variante.setCodigo("TD-1");
        variante.setNombre("Tornillo drywall · 1\"");
        variante.setAgrupador(general);
        variante.setCategoria(tornilleria);
        variante.setUnidadMedida(und);
        variante.setStockMinimo(BigDecimal.ONE);
        variante.setCombinacion(VariantesJson.combinacion(Map.of("Medida", "1\"")));
        when(productos.findWithRelacionesById(50L)).thenReturn(Optional.of(general));
        when(productos.findWithRelacionesById(51L)).thenReturn(Optional.of(variante));
        when(productos.findByAgrupadorIdOrderByCodigo(50L)).thenReturn(List.of(variante));
        when(stock.totalesDeAgrupadores(List.of(50L))).thenReturn(List.of());
        when(stock.totalDeProducto(51L)).thenReturn(BigDecimal.ZERO);
    }

    private CategoriaProducto categoria(Long id) {
        CategoriaProducto c = new CategoriaProducto();
        c.setId(id);
        when(categorias.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    private UnidadMedida unidad(Long id, String codigo) {
        UnidadMedida u = new UnidadMedida();
        u.setId(id);
        u.setCodigo(codigo);
        u.setMagnitud(com.proyecto.supply_core.catalogo.enums.Magnitud.CANTIDAD);
        u.setFactorConversionBase(BigDecimal.ONE);
        when(unidades.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    /**
     * @param codigo    código
     * @param nombre    nombre
     * @param categoria categoría
     * @param unidad    unidad
     * @param tarifa    impuesto
     * @return edición completa
     */
    private static ProductoUpdateRequest edicion(String codigo, String nombre, Long categoria, Long unidad, Long tarifa) {
        return new ProductoUpdateRequest(codigo, nombre, null, categoria, unidad, new BigDecimal("4"),
                new BigDecimal("9"), new BigDecimal("100"), tarifa, null, null);
    }

    @Test
    void editarElGeneralPropagaNombreCategoriaUnidadEImpuestoASusVariantes() {
        var r = servicio.actualizar(50L, edicion("TD", "Tornillo para drywall", 2L, 2L, 5L));

        assertEquals("Tornillo para drywall · 1\"", variante.getNombre());
        assertEquals(ferreteria, variante.getCategoria());
        assertEquals(caja, variante.getUnidadMedida());
        assertEquals(iva5, variante.getTarifaImpuesto());
        assertEquals(0, r.stockMinimo().signum(), "el general no tiene mínimo propio");
        assertNull(r.stockMaximo());
        assertNull(r.costoReferencia());
    }

    @Test
    void laUnidadDelGeneralNoCambiaSiUnaVarianteTieneMovimientos() {
        when(movimientos.existsByProductoAgrupadorId(50L)).thenReturn(true);

        NegocioException e = assertThrows(NegocioException.class,
                () -> servicio.actualizar(50L, edicion("TD", "Tornillo drywall", 1L, 2L, null)));
        assertEquals("UNIDAD_EN_USO", e.getCodigo());
    }

    @Test
    void unaVarianteNoPuedeTenerOtraCategoriaUnidadOImpuesto() {
        for (ProductoUpdateRequest req : List.of(edicion("TD-1", "x", 2L, 1L, null), edicion("TD-1", "x", 1L, 2L, null),
                edicion("TD-1", "x", 1L, 1L, 5L))) {
            NegocioException e = assertThrows(NegocioException.class, () -> servicio.actualizar(51L, req));
            assertEquals("VARIANTE_HEREDA", e.getCodigo());
        }
    }

    @Test
    void editarUnaVarianteCambiaSusDatosPeroElNombreLoArmaElSistema() {
        var r = servicio.actualizar(51L, edicion("TD-1A", "Otro nombre", 1L, 1L, null));

        assertEquals("TD-1A", r.codigo());
        assertEquals("Tornillo drywall · 1\"", r.nombre());
        assertEquals(new BigDecimal("4"), r.stockMinimo());
        assertEquals(50L, r.agrupadorId());
    }

    @Test
    void desactivarElGeneralDesactivaSusVariantes() {
        variante.setActivo(true);

        servicio.cambiarEstado(50L, false);

        assertFalse(variante.isActivo());
    }

    @Test
    void elGeneralNoEntraANingunaOperacion() {
        NegocioException e = assertThrows(NegocioException.class, general::exigirNoAgrupador);
        assertEquals("PRODUCTO_AGRUPADOR", e.getCodigo());
        variante.exigirNoAgrupador();
    }
}
