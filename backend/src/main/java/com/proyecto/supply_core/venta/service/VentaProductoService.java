package com.proyecto.supply_core.venta.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository.TotalPorProducto;
import com.proyecto.supply_core.venta.dto.CategoriaParaVender;
import com.proyecto.supply_core.venta.dto.ProductoParaVender;
import com.proyecto.supply_core.venta.dto.ProductoParaVender.PrecioEnUnidad;
import com.proyecto.supply_core.venta.entity.ListaPrecio;
import com.proyecto.supply_core.venta.entity.PrecioProducto;
import com.proyecto.supply_core.venta.repository.ListaPrecioRepository;
import com.proyecto.supply_core.venta.repository.PrecioProductoRepository;

/**
 * Búsqueda de productos para vender (spec 001): por código, código de barras o nombre, con el precio vigente de la
 * lista y el stock de la bodega. No devuelve costos, márgenes ni el precio mínimo: el cobro (spec 004) los controla.
 */
@Service
public class VentaProductoService {

    /** Máximo de productos por búsqueda: el POS muestra pocos y se filtra escribiendo más. */
    static final int MAXIMO = 20;

    /** Máximo de productos al elegir una categoría (spec 027): una grilla de tarjetas sin buscar. */
    static final int MAXIMO_CATEGORIA = 60;

    private final PrecioProductoRepository precioRepository;
    private final ListaPrecioRepository listaRepository;
    private final BodegaRepository bodegaRepository;
    private final StockProductoBodegaRepository stockRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final PreciosService preciosService;

    /**
     * @param precioRepository precios vigentes y búsqueda
     * @param listaRepository  listas de precios
     * @param bodegaRepository bodegas (para validar que la bodega sea de la empresa)
     * @param stockRepository  stock total cuando no se pide bodega
     * @param unidadRepository códigos de las otras unidades
     * @param preciosService   crea la lista General si la empresa aún no la tiene
     */
    public VentaProductoService(PrecioProductoRepository precioRepository, ListaPrecioRepository listaRepository,
            BodegaRepository bodegaRepository, StockProductoBodegaRepository stockRepository,
            UnidadMedidaRepository unidadRepository, PreciosService preciosService) {
        this.precioRepository = precioRepository;
        this.listaRepository = listaRepository;
        this.bodegaRepository = bodegaRepository;
        this.stockRepository = stockRepository;
        this.unidadRepository = unidadRepository;
        this.preciosService = preciosService;
    }

    /**
     * Busca productos activos y vendibles por texto.
     *
     * @param q        código, código de barras o parte del nombre
     * @param listaId  lista de precios ({@code null} = General)
     * @param bodegaId bodega cuyo stock se muestra ({@code null} = todas)
     * @return hasta {@value #MAXIMO} productos, primero los que coinciden exacto por código o código de barras
     * @throws NoEncontradoException si la lista o la bodega no son de la empresa
     */
    @Transactional
    public List<ProductoParaVender> buscar(String q, Long listaId, Long bodegaId) {
        return buscar(q, listaId, bodegaId, null);
    }

    /**
     * Busca productos activos y vendibles por texto, por categoría o por las dos cosas (spec 027).
     * <ul>
     *   <li>Solo texto: como siempre, hasta {@value #MAXIMO}.</li>
     *   <li>Solo categoría: los productos de esa categoría por nombre, hasta {@value #MAXIMO_CATEGORIA}.</li>
     *   <li>Texto y categoría: la búsqueda por texto, dejando solo los de la categoría.</li>
     * </ul>
     *
     * @param q           código, código de barras o parte del nombre (puede ir vacío si hay categoría)
     * @param listaId     lista de precios ({@code null} = General)
     * @param bodegaId    bodega cuyo stock se muestra ({@code null} = todas)
     * @param categoriaId categoría ({@code null} = todas)
     * @return productos listos para el POS (vacío si no hay ni texto ni categoría)
     * @throws NoEncontradoException si la lista o la bodega no son de la empresa
     */
    @Transactional
    public List<ProductoParaVender> buscar(String q, Long listaId, Long bodegaId, Long categoriaId) {
        preciosService.asegurarBase();
        ListaPrecio lista = listaId == null
                ? listaRepository.findAllByOrderByEsGeneralDescNombreAsc().stream().filter(ListaPrecio::isEsGeneral)
                        .findFirst().orElseThrow(() -> new IllegalStateException("La empresa no tiene lista General"))
                : listaRepository.findById(listaId)
                        .orElseThrow(() -> new NoEncontradoException("Lista de precios no encontrada"));
        if (bodegaId != null && bodegaRepository.findById(bodegaId).isEmpty()) {
            throw new NoEncontradoException("Bodega no encontrada");
        }
        String texto = limpiar(q);
        List<Producto> productos;
        if (texto.isEmpty()) {
            if (categoriaId == null) {
                return List.of();
            }
            productos = precioRepository.paraVenderDeCategoria(categoriaId, PageRequest.of(0, MAXIMO_CATEGORIA));
        } else {
            String exacto = texto.toLowerCase(Locale.ROOT);
            String patron = escaparLike(exacto);
            productos = precioRepository.buscarParaVender(exacto, patron + "%", "%" + patron + "%", texto,
                    PageRequest.of(0, MAXIMO));
            if (categoriaId != null) {
                productos = deCategoria(productos, categoriaId);
            }
        }
        if (productos.isEmpty()) {
            return List.of();
        }
        List<Long> ids = productos.stream().map(Producto::getId).toList();
        Map<Long, List<PrecioProducto>> precios = precioRepository.vigentesDe(lista.getId(), ids).stream()
                .collect(Collectors.groupingBy(p -> p.getProducto().getId()));
        Map<Long, BigDecimal> stock = (bodegaId == null ? stockRepository.totalesDe(ids)
                : precioRepository.stockEnBodega(bodegaId, ids)).stream()
                .collect(Collectors.toMap(TotalPorProducto::getProductoId, TotalPorProducto::getTotal, BigDecimal::add));
        Map<Long, String> codigos = new HashMap<>();
        return productos.stream().map(p -> armar(p, precios.getOrDefault(p.getId(), List.of()),
                stock.getOrDefault(p.getId(), BigDecimal.ZERO), codigos)).toList();
    }

    /**
     * Categorías para los botones del POS y del pedido de venta (spec 027): activas, con productos vendibles y con
     * precio vigente en la lista General.
     *
     * @return categorías por nombre con cuántos productos tienen
     */
    @Transactional(readOnly = true)
    public List<CategoriaParaVender> categorias() {
        return precioRepository.categoriasParaVender().stream()
                .map(c -> new CategoriaParaVender(c.getId(), c.getNombre(), c.getProductos() == null ? 0 : c.getProductos()))
                .toList();
    }

    /**
     * @param productos   productos encontrados por texto
     * @param categoriaId categoría
     * @return solo los de esa categoría, en el mismo orden
     */
    static List<Producto> deCategoria(List<Producto> productos, Long categoriaId) {
        return productos.stream().filter(p -> p.getCategoria() != null && categoriaId.equals(p.getCategoria().getId()))
                .toList();
    }

    /**
     * @param p       producto
     * @param precios precios vigentes del producto en la lista
     * @param stock   existencias
     * @param codigos caché de códigos de unidad por id
     * @return producto listo para el POS
     */
    private ProductoParaVender armar(Producto p, List<PrecioProducto> precios, BigDecimal stock,
            Map<Long, String> codigos) {
        Long unidad = p.getUnidadMedida().getId();
        BigDecimal precio = precios.stream().filter(x -> x.getUnidadMedidaId().equals(unidad))
                .map(PrecioProducto::getPrecio).findFirst().orElse(null);
        List<PrecioEnUnidad> otras = precios.stream().filter(x -> !x.getUnidadMedidaId().equals(unidad))
                .map(x -> new PrecioEnUnidad(x.getUnidadMedidaId(), codigos.computeIfAbsent(x.getUnidadMedidaId(),
                        id -> unidadRepository.findById(id).map(UnidadMedida::getCodigo).orElse("?")), x.getPrecio()))
                .toList();
        return new ProductoParaVender(p.getId(), p.getCodigo(), p.getCodigoBarras(), p.getNombre(), unidad,
                p.getUnidadMedida().getCodigo(),
                p.getTarifaImpuesto() == null ? null : p.getTarifaImpuesto().getPorcentaje(),
                p.isPrecioIncluyeImpuesto(), precio, otras, stock);
    }

    /**
     * Quita espacios de los extremos y recorta a 100 caracteres.
     *
     * @param q texto buscado
     * @return texto limpio (vacío si no queda nada)
     */
    static String limpiar(String q) {
        if (q == null) {
            return "";
        }
        String t = q.trim();
        return t.length() > 100 ? t.substring(0, 100) : t;
    }

    /**
     * Escapa los comodines de {@code like} con {@code !} para que {@code %} y {@code _} se busquen como texto (la
     * consulta declara {@code escape '!'}).
     *
     * @param texto texto buscado
     * @return texto con {@code !}, {@code %} y {@code _} escapados
     */
    static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
