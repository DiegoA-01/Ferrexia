package com.proyecto.supply_core.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.inventario.dto.AtributoVariante;
import com.proyecto.supply_core.inventario.dto.CambioVariantesRequest;
import com.proyecto.supply_core.inventario.dto.ProductoConVariantesRequest;
import com.proyecto.supply_core.inventario.dto.ProductoResponse;
import com.proyecto.supply_core.inventario.dto.VarianteNuevaRequest;
import com.proyecto.supply_core.inventario.dto.VariantesResponse;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.venta.dto.PrecioRequest;
import com.proyecto.supply_core.venta.entity.ListaPrecio;
import com.proyecto.supply_core.venta.entity.TarifaImpuesto;
import com.proyecto.supply_core.venta.repository.ListaPrecioRepository;
import com.proyecto.supply_core.venta.repository.TarifaImpuestoRepository;
import com.proyecto.supply_core.venta.service.PreciosService;

/** Spec 028: un producto general arma sus variantes; cada una es un producto con su código, precio y stock. */
class VarianteServiceTest {

    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final CategoriaProductoRepository categorias = mock(CategoriaProductoRepository.class);
    private final UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
    private final TarifaImpuestoRepository tarifas = mock(TarifaImpuestoRepository.class);
    private final StockProductoBodegaRepository stock = mock(StockProductoBodegaRepository.class);
    private final ListaPrecioRepository listas = mock(ListaPrecioRepository.class);
    private final PreciosService precios = mock(PreciosService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final VarianteService servicio = new VarianteService(productos, categorias, unidades, tarifas, stock, listas,
            precios, auditoria);

    /** Productos guardados en esta prueba, en orden. */
    private final List<Producto> guardados = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        CategoriaProducto cat = new CategoriaProducto();
        cat.setId(1L);
        cat.setNombre("Tornillería");
        UnidadMedida und = new UnidadMedida();
        und.setId(1L);
        und.setCodigo("UND");
        und.setMagnitud(com.proyecto.supply_core.catalogo.enums.Magnitud.CANTIDAD);
        und.setFactorConversionBase(BigDecimal.ONE);
        when(categorias.findById(1L)).thenReturn(Optional.of(cat));
        when(unidades.findById(1L)).thenReturn(Optional.of(und));
        TarifaImpuesto iva = new TarifaImpuesto();
        iva.setId(19L);
        iva.setCodigo("IVA19");
        iva.setNombre("IVA 19 %");
        when(tarifas.findByCodigo("IVA19")).thenReturn(Optional.of(iva));
        ListaPrecio general = new ListaPrecio();
        general.setId(3L);
        general.setEsGeneral(true);
        when(listas.findAllByOrderByEsGeneralDescNombreAsc()).thenReturn(List.of(general));
        when(productos.save(any())).thenAnswer(i -> {
            Producto p = i.getArgument(0);
            if (p.getId() == null) {
                p.setId(ids.incrementAndGet());
            }
            guardados.add(p);
            return p;
        });
    }

    /**
     * @param valores pares atributo, valor
     * @return combinación en orden
     */
    private static Map<String, String> c(String... valores) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < valores.length; i += 2) {
            m.put(valores[i], valores[i + 1]);
        }
        return m;
    }

    /**
     * @param combinacion valores
     * @param codigo      código de la variante
     * @param precio      precio en la General, o {@code null}
     * @return fila de una variante
     */
    private static VarianteNuevaRequest fila(Map<String, String> combinacion, String codigo, String precio) {
        return new VarianteNuevaRequest(combinacion, codigo, null, new BigDecimal("100"),
                precio == null ? null : new BigDecimal(precio), BigDecimal.ONE, null);
    }

    /** @return alta de "Tornillo drywall" con Medida (3) × Color (2) = 6 variantes */
    private static ProductoConVariantesRequest tornillo(List<VarianteNuevaRequest> filas) {
        return new ProductoConVariantesRequest("TD", "Tornillo drywall", null, 1L, 1L, null,
                List.of(new AtributoVariante("Medida", List.of("1\"", "1 1/2\"", "2\"")),
                        new AtributoVariante("Color", List.of("Negro", "Blanco"))),
                filas);
    }

    /** @return una fila por cada una de las 6 combinaciones, sin precio */
    private static List<VarianteNuevaRequest> seisFilas() {
        List<VarianteNuevaRequest> filas = new ArrayList<>();
        int n = 1;
        for (String m : List.of("1\"", "1 1/2\"", "2\"")) {
            for (String col : List.of("Negro", "Blanco")) {
                filas.add(fila(c("Medida", m, "Color", col), "td-" + n++, null));
            }
        }
        return filas;
    }

    @Test
    void dosAtributosPorTresYDosValoresCreanSeisVariantesConSuCodigoYNombre() {
        VariantesResponse r = servicio.crear(tornillo(seisFilas()));

        assertEquals(6, r.variantes().size());
        assertTrue(r.agrupador().esAgrupador());
        Producto general = guardados.get(0);
        assertTrue(general.isEsAgrupador());
        assertFalse(general.isVendible());
        assertEquals(0, general.getStockMinimo().signum());
        ProductoResponse primera = r.variantes().get(0);
        assertEquals("TD-1", primera.codigo());
        assertEquals("Tornillo drywall · 1\" · Negro", primera.nombre());
        assertEquals(general.getId(), primera.agrupadorId());
        assertEquals(c("Medida", "1\"", "Color", "Negro"), primera.combinacion());
        assertEquals("IVA 19 %", primera.tarifaImpuesto(), "hereda el impuesto del general");
        assertEquals(2, r.atributos().size());
        verify(precios, never()).fijarPrecio(any());
    }

    @Test
    void conPrecioLoFijaEnLaListaGeneralParaCadaVariante() {
        List<VarianteNuevaRequest> filas = seisFilas().stream()
                .map(f -> new VarianteNuevaRequest(f.valores(), f.codigo(), null, null, new BigDecimal("350"),
                        BigDecimal.ZERO, null))
                .toList();

        servicio.crear(tornillo(filas));

        verify(precios).exigirEdicion();
        verify(precios, times(6)).fijarPrecio(any(PrecioRequest.class));
    }

    @Test
    void sinPermisoDePreciosNoCreaNada() {
        doThrow(new AccesoDenegadoException("no")).when(precios).exigirEdicion();
        List<VarianteNuevaRequest> filas = new ArrayList<>(seisFilas());
        filas.set(0, fila(filas.get(0).valores(), "TD-1", "500"));

        assertThrows(AccesoDenegadoException.class, () -> servicio.crear(tornillo(filas)));
        verify(productos, never()).save(any());
    }

    @Test
    void codigoRepetidoEntreVariantesOEnLaBaseDa409() {
        List<VarianteNuevaRequest> filas = new ArrayList<>(seisFilas());
        filas.set(1, fila(filas.get(1).valores(), "TD-1", null));
        NegocioException e = assertThrows(NegocioException.class, () -> servicio.crear(tornillo(filas)));
        assertEquals("CODIGO_DUPLICADO", e.getCodigo());

        when(productos.existsByCodigoIgnoreCase("TD-3")).thenReturn(true);
        e = assertThrows(NegocioException.class, () -> servicio.crear(tornillo(seisFilas())));
        assertEquals("CODIGO_DUPLICADO", e.getCodigo());
        verify(productos, never()).save(any());
    }

    @Test
    void codigoDeBarrasRepetidoDa409() {
        List<VarianteNuevaRequest> filas = new ArrayList<>(seisFilas());
        VarianteNuevaRequest f = filas.get(0);
        filas.set(0, new VarianteNuevaRequest(f.valores(), f.codigo(), "770123", null, null, BigDecimal.ZERO, null));
        when(productos.existsByCodigoBarras("770123")).thenReturn(true);

        NegocioException e = assertThrows(NegocioException.class, () -> servicio.crear(tornillo(filas)));
        assertEquals("CODIGO_BARRAS_DUPLICADO", e.getCodigo());
    }

    @Test
    void masDeCienCombinacionesDa400() {
        List<String> once = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");
        var req = new ProductoConVariantesRequest("X", "X", null, 1L, 1L, null,
                List.of(new AtributoVariante("A", once), new AtributoVariante("B", once)), seisFilas());

        assertThrows(ParametroInvalidoException.class, () -> servicio.crear(req));
    }

    @Test
    void faltaUnaCombinacionOValorRepetidoDa400() {
        List<VarianteNuevaRequest> cinco = new ArrayList<>(seisFilas());
        cinco.remove(5);
        assertThrows(ParametroInvalidoException.class, () -> servicio.crear(tornillo(cinco)));

        var repetido = new ProductoConVariantesRequest("TD", "Tornillo", null, 1L, 1L, null,
                List.of(new AtributoVariante("Medida", List.of("1\"", " 1\" "))), List.of());
        assertThrows(ParametroInvalidoException.class, () -> servicio.crear(repetido));
        verify(productos, never()).save(any());
    }

    /** @return producto general ya guardado con Medida 1" y 2" y sus dos variantes */
    private Producto generalConDosVariantes(List<Producto> variantes) {
        Producto g = new Producto();
        g.setId(50L);
        g.setCodigo("TD");
        g.setNombre("Tornillo drywall");
        g.setEsAgrupador(true);
        g.setActivo(true);
        g.setStockMinimo(BigDecimal.ZERO);
        g.setCategoria(categorias.findById(1L).orElseThrow());
        g.setUnidadMedida(unidades.findById(1L).orElseThrow());
        g.setAtributos(VariantesJson.atributos(List.of(new AtributoVariante("Medida", List.of("1\"", "2\"")))));
        for (String m : List.of("1\"", "2\"")) {
            Producto v = new Producto();
            v.setId(ids.incrementAndGet());
            v.setCodigo("TD-" + m.charAt(0));
            v.setAgrupador(g);
            v.setActivo(true);
            v.setStockMinimo(BigDecimal.ZERO);
            v.setCategoria(g.getCategoria());
            v.setUnidadMedida(g.getUnidadMedida());
            v.setCombinacion(VariantesJson.combinacion(c("Medida", m)));
            variantes.add(v);
        }
        when(productos.findWithRelacionesById(50L)).thenReturn(Optional.of(g));
        when(productos.findByAgrupadorIdOrderByCodigo(50L)).thenAnswer(i -> new ArrayList<>(variantes));
        return g;
    }

    @Test
    void agregarUnValorCreaSuVarianteYQuitarOtroLaDesactiva() {
        List<Producto> variantes = new ArrayList<>();
        Producto g = generalConDosVariantes(variantes);

        servicio.cambiarValores(50L, new CambioVariantesRequest(
                List.of(new AtributoVariante("medida", List.of("1\"", "3\""))),
                List.of(fila(c("Medida", "3\""), "TD-3", null))));

        Producto nueva = guardados.get(0);
        assertEquals("TD-3", nueva.getCodigo());
        assertEquals("Tornillo drywall · 3\"", nueva.getNombre());
        assertEquals(g, nueva.getAgrupador());
        assertTrue(variantes.get(0).isActivo(), "1\" sigue");
        assertFalse(variantes.get(1).isActivo(), "2\" salió: se desactiva, no se borra");
        assertTrue(g.getAtributos().contains("3\\\""));
        assertTrue(g.getAtributos().contains("Medida"), "conserva el nombre del atributo como se creó");
    }

    @Test
    void volverAPonerUnValorReactivaSuVariante() {
        List<Producto> variantes = new ArrayList<>();
        generalConDosVariantes(variantes);
        variantes.get(1).setActivo(false);

        servicio.cambiarValores(50L, new CambioVariantesRequest(
                List.of(new AtributoVariante("Medida", List.of("1\"", "2\""))), List.of()));

        assertTrue(variantes.get(1).isActivo());
        verify(productos, never()).save(any());
    }

    @Test
    void unaCombinacionNuevaSinDatosDa400YCambiarElAtributoDa409() {
        List<Producto> variantes = new ArrayList<>();
        generalConDosVariantes(variantes);

        assertThrows(ParametroInvalidoException.class, () -> servicio.cambiarValores(50L, new CambioVariantesRequest(
                List.of(new AtributoVariante("Medida", List.of("1\"", "2\"", "3\""))), List.of())));
        NegocioException e = assertThrows(NegocioException.class, () -> servicio.cambiarValores(50L,
                new CambioVariantesRequest(List.of(new AtributoVariante("Calibre", List.of("8"))), List.of())));
        assertEquals("ATRIBUTOS_FIJOS", e.getCodigo());
    }

    @Test
    void unProductoSinVariantesNoTieneVariantes() {
        Producto simple = new Producto();
        simple.setId(9L);
        when(productos.findWithRelacionesById(9L)).thenReturn(Optional.of(simple));

        assertThrows(NoEncontradoException.class, () -> servicio.variantes(9L));
    }

    @Test
    void lasCombinacionesSalenEnElOrdenDeLosAtributos() {
        var combinaciones = VariantesJson.combinaciones(List.of(new AtributoVariante("A", List.of("1", "2")),
                new AtributoVariante("B", List.of("x", "y", "z"))));

        assertEquals(6, combinaciones.size());
        assertEquals(c("A", "1", "B", "x"), combinaciones.get(0));
        assertEquals(c("A", "2", "B", "z"), combinaciones.get(5));
        assertNull(VariantesJson.combinacion((String) null));
        verify(productos, never()).existsByCodigoBarras(anyString());
    }
}
