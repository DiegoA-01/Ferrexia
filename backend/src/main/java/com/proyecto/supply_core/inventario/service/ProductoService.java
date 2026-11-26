package com.proyecto.supply_core.inventario.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.inventario.dto.ProductoPlazoRequest;
import com.proyecto.supply_core.inventario.dto.ProductoRequest;
import com.proyecto.supply_core.inventario.dto.ProductoResponse;
import com.proyecto.supply_core.inventario.dto.ProductoUpdateRequest;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.MovimientoInventarioRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository.TotalPorProducto;

/**
 * UC-02 Gestionar catálogo de productos. Código único; desactivar impide usarlo en nuevas
 * operaciones sin borrar su historial. Este Service NO toca el stock.
 */
@Service
public class ProductoService {

    /** Campos por los que se puede ordenar el listado. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("codigo", "nombre");

    private final ProductoRepository productoRepository;
    private final CategoriaProductoRepository categoriaRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final StockProductoBodegaRepository stockRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final AuditoriaService auditoriaService;
    /** Impuesto de venta por defecto de los productos nuevos; {@code null} en pruebas que no lo usan. */
    private final com.proyecto.supply_core.venta.repository.TarifaImpuestoRepository tarifaRepository;
    /** Proveedor del costo de referencia (spec 026); {@code null} en pruebas que no lo usan. */
    private final com.proyecto.supply_core.proveedor.repository.ProveedorRepository proveedorRepository;

    /**
     * @param productoRepository   acceso a productos
     * @param categoriaRepository  acceso a categorías
     * @param unidadRepository     acceso a unidades
     * @param stockRepository      totales de stock (solo lectura)
     * @param movimientoRepository para saber si el producto ya tiene movimientos
     * @param auditoriaService     registro de cambios
     */
    public ProductoService(ProductoRepository productoRepository, CategoriaProductoRepository categoriaRepository,
            UnidadMedidaRepository unidadRepository, StockProductoBodegaRepository stockRepository,
            MovimientoInventarioRepository movimientoRepository, AuditoriaService auditoriaService) {
        this(productoRepository, categoriaRepository, unidadRepository, stockRepository, movimientoRepository,
                auditoriaService, null);
    }

    /**
     * @param productoRepository   acceso a productos
     * @param categoriaRepository  acceso a categorías
     * @param unidadRepository     acceso a unidades
     * @param stockRepository      totales de stock (solo lectura)
     * @param movimientoRepository para saber si el producto ya tiene movimientos
     * @param auditoriaService     registro de cambios
     * @param tarifaRepository     impuesto de venta por defecto (IVA 19 %) de los productos nuevos
     */
    public ProductoService(ProductoRepository productoRepository, CategoriaProductoRepository categoriaRepository,
            UnidadMedidaRepository unidadRepository, StockProductoBodegaRepository stockRepository,
            MovimientoInventarioRepository movimientoRepository, AuditoriaService auditoriaService,
            com.proyecto.supply_core.venta.repository.TarifaImpuestoRepository tarifaRepository) {
        this(productoRepository, categoriaRepository, unidadRepository, stockRepository, movimientoRepository,
                auditoriaService, tarifaRepository, null);
    }

    /**
     * @param productoRepository   acceso a productos
     * @param categoriaRepository  acceso a categorías
     * @param unidadRepository     acceso a unidades
     * @param stockRepository      totales de stock (solo lectura)
     * @param movimientoRepository para saber si el producto ya tiene movimientos
     * @param auditoriaService     registro de cambios
     * @param tarifaRepository     impuestos de venta de la empresa
     * @param proveedorRepository  proveedor del costo de referencia (spec 026)
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ProductoService(ProductoRepository productoRepository, CategoriaProductoRepository categoriaRepository,
            UnidadMedidaRepository unidadRepository, StockProductoBodegaRepository stockRepository,
            MovimientoInventarioRepository movimientoRepository, AuditoriaService auditoriaService,
            com.proyecto.supply_core.venta.repository.TarifaImpuestoRepository tarifaRepository,
            com.proyecto.supply_core.proveedor.repository.ProveedorRepository proveedorRepository) {
        this.tarifaRepository = tarifaRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.unidadRepository = unidadRepository;
        this.stockRepository = stockRepository;
        this.movimientoRepository = movimientoRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista productos con filtros opcionales y su stock total.
     *
     * @param texto       coincidencia en código o nombre
     * @param categoriaId categoría
     * @param activo      estado
     * @param pageable    página y orden (solo campos de {@link #CAMPOS_ORDENABLES})
     * @return página de productos
     * @throws ParametroInvalidoException si se pide ordenar por un campo no permitido
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductoResponse> buscar(String texto, Long categoriaId, Boolean activo, Pageable pageable) {
        String filtro = (texto == null || texto.isBlank()) ? null : texto.trim();
        Page<Producto> pagina = productoRepository.buscar(filtro, categoriaId, activo,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES));
        Map<Long, BigDecimal> totales = totales(pagina.getContent());
        return PageResponse.of(pagina, p -> ProductoResponse.of(p, totales.getOrDefault(p.getId(), cero())));
    }

    /**
     * Detalle de un producto con su stock total.
     *
     * @param id id
     * @return producto
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        Producto p = buscarEntidad(id);
        return ProductoResponse.of(p, stockRepository.totalDeProducto(id));
    }

    /**
     * Productos activos por debajo de su stock mínimo (alertas de reposición, paso 1 del flujo).
     *
     * @return productos con su stock total
     */
    @Transactional(readOnly = true)
    public List<ProductoResponse> alertasStockMinimo() {
        List<Producto> productos = productoRepository.bajoStockMinimo();
        Map<Long, BigDecimal> totales = totales(productos);
        return productos.stream().map(p -> ProductoResponse.of(p, totales.getOrDefault(p.getId(), cero()))).toList();
    }

    /**
     * Crea un producto (el request ya validó código único y categoría/unidad activas).
     *
     * @param req datos validados
     * @return producto creado (stock 0)
     */
    @Transactional
    public ProductoResponse crear(ProductoRequest req) {
        Producto p = new Producto();
        p.setCodigo(normalizarCodigo(req.codigo()));
        if (req.tarifaImpuestoId() != null) {
            p.setTarifaImpuesto(tarifa(req.tarifaImpuestoId()));
        } else if (tarifaRepository != null) {
            // como en la migración V29: sin elegir, los productos nuevos arrancan con IVA 19 %
            tarifaRepository.findByCodigo("IVA19").ifPresent(p::setTarifaImpuesto);
        }
        aplicar(p, req.nombre(), req.descripcion(), categoria(req.categoriaId()), unidad(req.unidadMedidaId()),
                req.stockMinimo(), req.stockMaximo(), req.costoReferencia());
        if (req.proveedorReferenciaId() != null) {
            p.setProveedorReferencia(proveedor(req.proveedorReferenciaId()));
        }
        // spec 026: el costo de referencia lleva su fecha; si no se escribe, es la de hoy
        p.setCostoReferenciaFecha(req.costoReferenciaFecha() != null ? req.costoReferenciaFecha()
                : req.costoReferencia() != null ? java.time.LocalDate.now() : null);
        Producto guardado = productoRepository.save(p);
        auditoriaService.exito(AccionAuditoria.PRODUCTO_CREAR, EntidadAuditada.PRODUCTO, guardado.getId(),
                "codigo=" + guardado.getCodigo() + ", unidad=" + guardado.getUnidadMedida().getCodigo()
                        + (guardado.getTarifaImpuesto() == null ? "" : ", impuesto=" + guardado.getTarifaImpuesto().getCodigo())
                        + (guardado.getProveedorReferencia() == null ? ""
                                : ", proveedorReferencia=" + guardado.getProveedorReferencia().getId()));
        return ProductoResponse.of(guardado, cero());
    }

    /**
     * Productos activos con stock máximo y un total por encima de él (spec 026, D17: no bloquea, solo avisa).
     *
     * @return productos sobre el máximo con su stock total
     */
    @Transactional(readOnly = true)
    public List<ProductoResponse> sobreStockMaximo() {
        List<Producto> productos = productoRepository.sobreStockMaximo();
        Map<Long, BigDecimal> totales = totales(productos);
        return productos.stream().map(p -> ProductoResponse.of(p, totales.getOrDefault(p.getId(), cero()))).toList();
    }

    /**
     * Edita un producto.
     *
     * @param id  producto
     * @param req datos validados
     * @return producto actualizado
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si el código lo usa otro producto o se cambia la unidad con movimientos
     */
    @Transactional
    public ProductoResponse actualizar(Long id, ProductoUpdateRequest req) {
        Producto p = buscarEntidad(id);
        String codigo = normalizarCodigo(req.codigo());
        if (productoRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
            throw new NegocioException("CODIGO_DUPLICADO", "Ya existe un producto con el código " + codigo);
        }
        boolean cambiaUnidad = !p.getUnidadMedida().getId().equals(req.unidadMedidaId());
        if (cambiaUnidad && movimientoRepository.existsByProductoId(id)) {
            throw new NegocioException("UNIDAD_EN_USO",
                    "No se puede cambiar la unidad de un producto que ya tiene movimientos de inventario");
        }
        String antes = describir(p);
        p.setCodigo(codigo);
        aplicar(p, req.nombre(), req.descripcion(), categoria(req.categoriaId()),
                cambiaUnidad ? unidad(req.unidadMedidaId()) : p.getUnidadMedida(),
                req.stockMinimo(), req.stockMaximo(), req.costoReferencia());
        // spec 026: impuesto, proveedor y fecha del costo; lo que no se envía no cambia
        if (req.tarifaImpuestoId() != null) {
            p.setTarifaImpuesto(tarifa(req.tarifaImpuestoId()));
        }
        if (req.proveedorReferenciaId() != null) {
            p.setProveedorReferencia(proveedor(req.proveedorReferenciaId()));
        }
        if (req.costoReferenciaFecha() != null) {
            p.setCostoReferenciaFecha(req.costoReferenciaFecha());
        }
        auditoriaService.exito(AccionAuditoria.PRODUCTO_EDITAR, EntidadAuditada.PRODUCTO, id,
                "antes: " + antes + " | después: " + describir(p));
        return ProductoResponse.of(p, stockRepository.totalDeProducto(id));
    }

    /**
     * Cambia el plazo de reabastecimiento propio del producto. Un valor {@code null} vuelve al de la empresa.
     *
     * @param id  producto
     * @param req días hábiles de entrega y de seguridad
     * @return producto actualizado
     * @throws NoEncontradoException si no existe
     */
    @Transactional
    public ProductoResponse cambiarPlazo(Long id, ProductoPlazoRequest req) {
        Producto p = buscarEntidad(id);
        String antes = "plazoEntregaDias=" + p.getPlazoEntregaDias() + ", diasSeguridad=" + p.getDiasSeguridad();
        p.setPlazoEntregaDias(req.plazoEntregaDias());
        p.setDiasSeguridad(req.diasSeguridad());
        auditoriaService.exito(AccionAuditoria.PRODUCTO_EDITAR, EntidadAuditada.PRODUCTO, id, "codigo=" + p.getCodigo()
                + "; antes: " + antes + " | después: plazoEntregaDias=" + p.getPlazoEntregaDias() + ", diasSeguridad="
                + p.getDiasSeguridad());
        return ProductoResponse.of(p, stockRepository.totalDeProducto(id));
    }

    /**
     * Activa o desactiva un producto (desactivar impide nuevos movimientos; el stock se conserva).
     *
     * @param id     producto
     * @param activo nuevo estado
     * @return producto actualizado
     * @throws NoEncontradoException si no existe
     */
    @Transactional
    public ProductoResponse cambiarEstado(Long id, boolean activo) {
        Producto p = buscarEntidad(id);
        p.setActivo(activo);
        auditoriaService.exito(AccionAuditoria.PRODUCTO_CAMBIAR_ESTADO, EntidadAuditada.PRODUCTO, id,
                "codigo=" + p.getCodigo() + ", activo=" + activo);
        return ProductoResponse.of(p, stockRepository.totalDeProducto(id));
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Copia los datos editables a la entidad, normalizando textos y montos.
     *
     * @param p               producto destino
     * @param nombre          nombre
     * @param descripcion     descripción
     * @param categoria       categoría
     * @param unidad          unidad
     * @param stockMinimo     mínimo
     * @param stockMaximo     máximo o {@code null}
     * @param costoReferencia costo o {@code null}
     */
    private static void aplicar(Producto p, String nombre, String descripcion, CategoriaProducto categoria,
            UnidadMedida unidad, BigDecimal stockMinimo, BigDecimal stockMaximo, BigDecimal costoReferencia) {
        p.setNombre(nombre.trim());
        p.setDescripcion(descripcion == null || descripcion.isBlank() ? null : descripcion.trim());
        p.setCategoria(categoria);
        p.setUnidadMedida(unidad);
        p.setStockMinimo(stockMinimo);
        p.setStockMaximo(stockMaximo);
        p.setCostoReferencia(costoReferencia);
    }

    /**
     * Totales de stock de varios productos en una consulta.
     *
     * @param productos productos
     * @return total por id de producto
     */
    private Map<Long, BigDecimal> totales(List<Producto> productos) {
        if (productos.isEmpty()) {
            return Map.of();
        }
        return stockRepository.totalesDe(productos.stream().map(Producto::getId).toList()).stream()
                .collect(Collectors.toMap(TotalPorProducto::getProductoId, TotalPorProducto::getTotal));
    }

    /**
     * Busca el producto con categoría y unidad.
     *
     * @param id id
     * @return entidad
     * @throws NoEncontradoException si no existe
     */
    private Producto buscarEntidad(Long id) {
        return productoRepository.findWithRelacionesById(id).orElseThrow(() -> new NoEncontradoException("Producto", id));
    }

    /**
     * Carga la categoría ({@code @CategoriaActiva} ya validó que existe y está activa).
     *
     * @param id id de la categoría
     * @return categoría
     * @throws NoEncontradoException si no existe (solo si se llamó al Service sin {@code @Valid})
     */
    private CategoriaProducto categoria(Long id) {
        return categoriaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Categoría", id));
    }

    /**
     * Carga la unidad ({@code @UnidadMedidaActiva} ya validó que existe y está activa).
     *
     * @param id id de la unidad
     * @return unidad
     * @throws NoEncontradoException si no existe (solo si se llamó al Service sin {@code @Valid})
     */
    private UnidadMedida unidad(Long id) {
        return unidadRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Unidad de medida", id));
    }

    /**
     * @param id impuesto de venta
     * @return la tarifa activa de la empresa ({@code @TenantId} deja fuera las de otras)
     * @throws NegocioException {@code TARIFA_INVALIDA} si no es de la empresa o está inactiva
     */
    private com.proyecto.supply_core.venta.entity.TarifaImpuesto tarifa(Long id) {
        return (tarifaRepository == null ? java.util.Optional.<com.proyecto.supply_core.venta.entity.TarifaImpuesto>empty()
                : tarifaRepository.findById(id)).filter(com.proyecto.supply_core.venta.entity.TarifaImpuesto::isActivo)
                .orElseThrow(() -> new NegocioException("TARIFA_INVALIDA", "Elija un impuesto activo de la empresa"));
    }

    /**
     * @param id proveedor
     * @return el proveedor de la empresa ({@code @TenantId} deja fuera los de otras)
     * @throws NegocioException {@code PROVEEDOR_INVALIDO} si no es de la empresa
     */
    private com.proyecto.supply_core.proveedor.entity.Proveedor proveedor(Long id) {
        return (proveedorRepository == null ? java.util.Optional.<com.proyecto.supply_core.proveedor.entity.Proveedor>empty()
                : proveedorRepository.findById(id))
                .orElseThrow(() -> new NegocioException("PROVEEDOR_INVALIDO", "El proveedor no es de la empresa"));
    }

    /**
     * Resumen para la auditoría.
     *
     * @param p producto
     * @return texto con código, nombre, categoría, unidad y mínimo
     */
    private static String describir(Producto p) {
        return "codigo=" + p.getCodigo() + ", nombre=" + p.getNombre() + ", categoria=" + p.getCategoria().getId()
                + ", unidad=" + p.getUnidadMedida().getCodigo() + ", minimo=" + p.getStockMinimo().toPlainString();
    }

    /**
     * Normaliza el código para guardarlo y compararlo siempre igual.
     *
     * @param codigo código recibido
     * @return código sin espacios y en mayúsculas
     */
    private static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Cero con la escala de las cantidades.
     *
     * @return 0.0000
     */
    private static BigDecimal cero() {
        return BigDecimal.ZERO.setScale(4);
    }
}
