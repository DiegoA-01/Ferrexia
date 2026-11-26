package com.proyecto.supply_core.inventario.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
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
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository.TotalPorProducto;
import com.proyecto.supply_core.venta.dto.PrecioRequest;
import com.proyecto.supply_core.venta.entity.ListaPrecio;
import com.proyecto.supply_core.venta.entity.TarifaImpuesto;
import com.proyecto.supply_core.venta.repository.ListaPrecioRepository;
import com.proyecto.supply_core.venta.repository.TarifaImpuestoRepository;
import com.proyecto.supply_core.venta.service.PreciosService;

/**
 * Productos con variantes (spec 028). Cada variante es un producto normal (código, código de barras, stock, kardex y
 * precio propios) que apunta a un producto general (agrupador). El general no tiene stock, no se vende ni se compra:
 * guarda los atributos y lo que heredan sus variantes (categoría, unidad e impuesto). Java arma las combinaciones; el
 * stock sigue cambiando solo por {@code MovimientoInventarioService}.
 */
@Service
public class VarianteService {

    /** Máximo de combinaciones por producto (spec 028). */
    public static final int MAX_COMBINACIONES = 100;

    private final ProductoRepository productoRepository;
    private final CategoriaProductoRepository categoriaRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final TarifaImpuestoRepository tarifaRepository;
    private final StockProductoBodegaRepository stockRepository;
    private final ListaPrecioRepository listaRepository;
    private final PreciosService preciosService;
    private final AuditoriaService auditoriaService;

    /**
     * @param productoRepository  productos
     * @param categoriaRepository categorías
     * @param unidadRepository    unidades
     * @param tarifaRepository    impuestos de venta
     * @param stockRepository     totales de stock (solo lectura)
     * @param listaRepository     listas de precios (la General)
     * @param preciosService      fija el precio de cada variante en la General
     * @param auditoriaService    registro de cambios
     */
    public VarianteService(ProductoRepository productoRepository, CategoriaProductoRepository categoriaRepository,
            UnidadMedidaRepository unidadRepository, TarifaImpuestoRepository tarifaRepository,
            StockProductoBodegaRepository stockRepository, ListaPrecioRepository listaRepository,
            PreciosService preciosService, AuditoriaService auditoriaService) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.unidadRepository = unidadRepository;
        this.tarifaRepository = tarifaRepository;
        this.stockRepository = stockRepository;
        this.listaRepository = listaRepository;
        this.preciosService = preciosService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Crea el producto general y una variante por cada combinación de valores, en una sola transacción.
     *
     * @param req datos comunes, atributos y una fila por combinación
     * @return el producto general con sus variantes
     * @throws ParametroInvalidoException si los atributos repiten nombres o valores, pasan de 100 combinaciones o las
     *                                    filas no son exactamente una por combinación
     * @throws NegocioException           {@code CODIGO_DUPLICADO} o {@code CODIGO_BARRAS_DUPLICADO}
     * @throws AccesoDenegadoException    si trae precios y la sesión no puede fijarlos
     */
    @Transactional
    public VariantesResponse crear(ProductoConVariantesRequest req) {
        List<AtributoVariante> atributos = limpiar(req.atributos());
        List<Map<String, String>> combinaciones = combinaciones(atributos);
        Map<String, VarianteNuevaRequest> filas = filasPorCombinacion(atributos, req.variantes());
        Set<String> faltan = new HashSet<>();
        for (Map<String, String> c : combinaciones) {
            if (!filas.containsKey(VariantesJson.clave(c))) {
                faltan.add(String.join(" / ", c.values()));
            }
        }
        if (!faltan.isEmpty() || filas.size() != combinaciones.size()) {
            throw new ParametroInvalidoException("Debe venir una fila por cada combinación"
                    + (faltan.isEmpty() ? "" : "; faltan: " + String.join(", ", faltan)));
        }
        String codigoGeneral = codigo(req.codigo());
        validarCodigos(codigoGeneral, req.variantes());
        exigirPreciosSiVienen(req.variantes());

        Producto general = new Producto();
        general.setCodigo(codigoGeneral);
        general.setNombre(req.nombre().trim());
        general.setDescripcion(req.descripcion() == null || req.descripcion().isBlank() ? null : req.descripcion().trim());
        general.setCategoria(categoriaRepository.findById(req.categoriaId())
                .orElseThrow(() -> new NoEncontradoException("Categoría", req.categoriaId())));
        general.setUnidadMedida(unidadRepository.findById(req.unidadMedidaId())
                .orElseThrow(() -> new NoEncontradoException("Unidad de medida", req.unidadMedidaId())));
        general.setTarifaImpuesto(tarifa(req.tarifaImpuestoId()));
        general.setStockMinimo(BigDecimal.ZERO);
        general.setEsAgrupador(true);
        general.setVendible(false);
        general.setAtributos(VariantesJson.atributos(atributos));
        Producto guardado = productoRepository.save(general);

        List<Producto> variantes = new ArrayList<>();
        for (Map<String, String> c : combinaciones) {
            variantes.add(crearVariante(guardado, c, filas.get(VariantesJson.clave(c))));
        }
        auditoriaService.exito(AccionAuditoria.PRODUCTO_CREAR, EntidadAuditada.PRODUCTO, guardado.getId(),
                "codigo=" + guardado.getCodigo() + ", conVariantes=" + variantes.size() + ", atributos="
                        + atributos.stream().map(AtributoVariante::nombre).collect(Collectors.joining("/")));
        return respuesta(guardado, atributos, variantes);
    }

    /**
     * @param id producto general
     * @return el producto general con sus atributos, su stock total y sus variantes
     * @throws NoEncontradoException si no existe o no tiene variantes
     */
    @Transactional(readOnly = true)
    public VariantesResponse variantes(Long id) {
        Producto general = general(id);
        return respuesta(general, VariantesJson.atributos(general.getAtributos()),
                productoRepository.findByAgrupadorIdOrderByCodigo(id));
    }

    /**
     * Cambia los valores de los atributos: las combinaciones nuevas se crean (con los datos que vienen), las que ya
     * existían se reactivan y las que desaparecen se desactivan. Nada se borra.
     *
     * @param id  producto general
     * @param req mismos atributos con sus valores nuevos y los datos de las combinaciones nuevas
     * @return el producto general con sus variantes
     * @throws NoEncontradoException      si no existe o no tiene variantes
     * @throws NegocioException           {@code ATRIBUTOS_FIJOS} si cambian los nombres de los atributos, o códigos
     *                                    repetidos
     * @throws ParametroInvalidoException si una combinación nueva no trae sus datos o pasan de 100
     */
    @Transactional
    public VariantesResponse cambiarValores(Long id, CambioVariantesRequest req) {
        Producto general = general(id);
        List<AtributoVariante> antes = VariantesJson.atributos(general.getAtributos());
        List<AtributoVariante> atributos = limpiar(req.atributos());
        List<String> nombresAntes = antes.stream().map(a -> a.nombre().toLowerCase(Locale.ROOT)).toList();
        List<String> nombresAhora = atributos.stream().map(a -> a.nombre().toLowerCase(Locale.ROOT)).toList();
        if (!nombresAntes.equals(nombresAhora)) {
            throw new NegocioException("ATRIBUTOS_FIJOS", "Los atributos (" + antes.stream().map(AtributoVariante::nombre)
                    .collect(Collectors.joining(", ")) + ") no cambian: solo se agregan o quitan valores");
        }
        // conserva los nombres de atributo como se escribieron al crear
        List<AtributoVariante> fijos = new ArrayList<>();
        for (int i = 0; i < atributos.size(); i++) {
            fijos.add(new AtributoVariante(antes.get(i).nombre(), atributos.get(i).valores()));
        }
        List<Map<String, String>> combinaciones = combinaciones(fijos);
        List<Producto> existentes = productoRepository.findByAgrupadorIdOrderByCodigo(id);
        Map<String, Producto> porClave = new HashMap<>();
        for (Producto v : existentes) {
            porClave.put(VariantesJson.clave(VariantesJson.combinacion(v.getCombinacion())), v);
        }
        Map<String, VarianteNuevaRequest> filas = filasPorCombinacion(fijos, req.variantes());
        Set<String> vigentes = new HashSet<>();
        List<VarianteNuevaRequest> nuevas = new ArrayList<>();
        List<Map<String, String>> combinacionesNuevas = new ArrayList<>();
        for (Map<String, String> c : combinaciones) {
            String clave = VariantesJson.clave(c);
            vigentes.add(clave);
            if (!porClave.containsKey(clave)) {
                VarianteNuevaRequest fila = filas.get(clave);
                if (fila == null) {
                    throw new ParametroInvalidoException("Faltan los datos de la variante " + String.join(" / ", c.values()));
                }
                nuevas.add(fila);
                combinacionesNuevas.add(c);
            }
        }
        validarCodigos(null, nuevas);
        exigirPreciosSiVienen(nuevas);
        int reactivadas = 0;
        int desactivadas = 0;
        for (Map.Entry<String, Producto> e : porClave.entrySet()) {
            Producto v = e.getValue();
            boolean queda = vigentes.contains(e.getKey());
            if (queda && !v.isActivo() && general.isActivo()) {
                v.setActivo(true);
                reactivadas++;
            } else if (!queda && v.isActivo()) {
                v.setActivo(false);
                desactivadas++;
            }
        }
        for (int i = 0; i < nuevas.size(); i++) {
            crearVariante(general, combinacionesNuevas.get(i), nuevas.get(i));
        }
        general.setAtributos(VariantesJson.atributos(fijos));
        auditoriaService.exito(AccionAuditoria.PRODUCTO_EDITAR, EntidadAuditada.PRODUCTO, id, "codigo="
                + general.getCodigo() + "; variantes: nuevas=" + nuevas.size() + ", reactivadas=" + reactivadas
                + ", desactivadas=" + desactivadas);
        return respuesta(general, fijos, productoRepository.findByAgrupadorIdOrderByCodigo(id));
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Crea una variante con lo que hereda del general y, si trae precio, lo fija en la lista General.
     *
     * @param general     producto general guardado
     * @param combinacion valor por atributo
     * @param fila        datos de la variante
     * @return variante guardada
     */
    private Producto crearVariante(Producto general, Map<String, String> combinacion, VarianteNuevaRequest fila) {
        Producto v = new Producto();
        v.setCodigo(codigo(fila.codigo()));
        v.setAgrupador(general);
        v.setCombinacion(VariantesJson.combinacion(combinacion));
        v.setNombre(ProductoService.nombreVariante(general.getNombre(), v));
        v.setDescripcion(general.getDescripcion());
        v.setCategoria(general.getCategoria());
        v.setUnidadMedida(general.getUnidadMedida());
        v.setTarifaImpuesto(general.getTarifaImpuesto());
        v.setCodigoBarras(barras(fila.codigoBarras()));
        v.setStockMinimo(fila.stockMinimo());
        v.setStockMaximo(fila.stockMaximo());
        v.setCostoReferencia(fila.costoReferencia());
        v.setCostoReferenciaFecha(fila.costoReferencia() == null ? null : java.time.LocalDate.now());
        Producto guardada = productoRepository.save(v);
        if (fila.precio() != null) {
            preciosService.fijarPrecio(new PrecioRequest(listaGeneral().getId(), guardada.getId(),
                    fila.precio().setScale(2, RoundingMode.HALF_UP), null, "Alta con variantes", null));
        }
        return guardada;
    }

    /**
     * Limpia espacios y verifica que los atributos y sus valores no se repitan y que no pasen de 100 combinaciones.
     *
     * @param atributos atributos recibidos
     * @return atributos limpios, en el mismo orden
     * @throws ParametroInvalidoException si hay repetidos o demasiadas combinaciones
     */
    private static List<AtributoVariante> limpiar(List<AtributoVariante> atributos) {
        List<AtributoVariante> limpios = new ArrayList<>();
        Set<String> nombres = new HashSet<>();
        for (AtributoVariante a : atributos) {
            String nombre = a.nombre().trim();
            if (!nombres.add(nombre.toLowerCase(Locale.ROOT))) {
                throw new ParametroInvalidoException("El atributo " + nombre + " está repetido");
            }
            Set<String> vistos = new HashSet<>();
            List<String> valores = new ArrayList<>();
            for (String valor : a.valores()) {
                String limpio = valor.trim();
                if (!vistos.add(limpio.toLowerCase(Locale.ROOT))) {
                    throw new ParametroInvalidoException("El valor " + limpio + " de " + nombre + " está repetido");
                }
                valores.add(limpio);
            }
            limpios.add(new AtributoVariante(nombre, valores));
        }
        if (VariantesJson.cuantas(limpios) > MAX_COMBINACIONES) {
            throw new ParametroInvalidoException("Salen " + VariantesJson.cuantas(limpios)
                    + " combinaciones; el máximo es " + MAX_COMBINACIONES);
        }
        return limpios;
    }

    /**
     * @param atributos atributos limpios
     * @return combinaciones posibles
     */
    private static List<Map<String, String>> combinaciones(List<AtributoVariante> atributos) {
        return VariantesJson.combinaciones(atributos);
    }

    /**
     * Ordena las filas recibidas por combinación, con los nombres de atributo como están en el producto.
     *
     * @param atributos atributos limpios
     * @param filas     filas recibidas
     * @return fila por clave de combinación
     * @throws ParametroInvalidoException si una fila nombra un atributo o valor que no existe, o se repite
     */
    private static Map<String, VarianteNuevaRequest> filasPorCombinacion(List<AtributoVariante> atributos,
            List<VarianteNuevaRequest> filas) {
        Map<String, VarianteNuevaRequest> resultado = new LinkedHashMap<>();
        for (VarianteNuevaRequest f : filas) {
            Map<String, String> c = new LinkedHashMap<>();
            Map<String, String> recibidos = new HashMap<>();
            f.valores().forEach((k, v) -> recibidos.put(k.trim().toLowerCase(Locale.ROOT), v == null ? "" : v.trim()));
            for (AtributoVariante a : atributos) {
                String valor = recibidos.get(a.nombre().toLowerCase(Locale.ROOT));
                String propio = valor == null ? null : a.valores().stream().filter(x -> x.equalsIgnoreCase(valor))
                        .findFirst().orElse(null);
                if (propio == null) {
                    throw new ParametroInvalidoException("La variante " + f.codigo() + " no trae un valor válido de "
                            + a.nombre());
                }
                c.put(a.nombre(), propio);
            }
            if (recibidos.size() != atributos.size()) {
                throw new ParametroInvalidoException("La variante " + f.codigo() + " trae atributos que no existen");
            }
            if (resultado.put(VariantesJson.clave(c), f) != null) {
                throw new ParametroInvalidoException("La combinación " + String.join(" / ", c.values())
                        + " viene repetida");
            }
        }
        return resultado;
    }

    /**
     * Códigos y códigos de barras únicos entre sí y en la empresa.
     *
     * @param codigoGeneral código del producto general, o {@code null} si ya existe
     * @param filas         variantes nuevas
     * @throws NegocioException {@code CODIGO_DUPLICADO} o {@code CODIGO_BARRAS_DUPLICADO}
     */
    private void validarCodigos(String codigoGeneral, List<VarianteNuevaRequest> filas) {
        Set<String> codigos = new HashSet<>();
        Set<String> barras = new HashSet<>();
        if (codigoGeneral != null) {
            codigos.add(codigoGeneral);
            if (productoRepository.existsByCodigoIgnoreCase(codigoGeneral)) {
                throw new NegocioException("CODIGO_DUPLICADO", "Ya existe un producto con el código " + codigoGeneral);
            }
        }
        for (VarianteNuevaRequest f : filas) {
            String c = codigo(f.codigo());
            if (!codigos.add(c) || productoRepository.existsByCodigoIgnoreCase(c)) {
                throw new NegocioException("CODIGO_DUPLICADO", "Ya existe un producto con el código " + c);
            }
            String b = barras(f.codigoBarras());
            if (b != null && (!barras.add(b) || productoRepository.existsByCodigoBarras(b))) {
                throw new NegocioException("CODIGO_BARRAS_DUPLICADO", "Otro producto ya usa el código de barras " + b);
            }
        }
    }

    /**
     * Si alguna fila trae precio, la sesión debe poder fijar precios (se revisa antes de crear nada).
     *
     * @param filas variantes nuevas
     * @throws AccesoDenegadoException si no puede
     */
    private void exigirPreciosSiVienen(List<VarianteNuevaRequest> filas) {
        if (filas.stream().anyMatch(f -> f.precio() != null)) {
            preciosService.exigirEdicion();
            listaGeneral();
        }
    }

    /**
     * @return la lista de precios General de la empresa
     * @throws NegocioException {@code SIN_LISTA_GENERAL} si no hay
     */
    private ListaPrecio listaGeneral() {
        return listaRepository.findAllByOrderByEsGeneralDescNombreAsc().stream().filter(ListaPrecio::isEsGeneral)
                .findFirst().orElseThrow(() -> new NegocioException("SIN_LISTA_GENERAL",
                        "La empresa no tiene lista de precios General; fije los precios en Precios y margen"));
    }

    /**
     * @param id impuesto pedido, o {@code null} para el IVA 19 % como un producto nuevo
     * @return impuesto activo de la empresa, o {@code null} si no hay IVA 19 %
     * @throws NegocioException {@code TARIFA_INVALIDA} si no es de la empresa o está inactivo
     */
    private TarifaImpuesto tarifa(Long id) {
        if (id == null) {
            return tarifaRepository.findByCodigo("IVA19").orElse(null);
        }
        return tarifaRepository.findById(id).filter(TarifaImpuesto::isActivo)
                .orElseThrow(() -> new NegocioException("TARIFA_INVALIDA", "Elija un impuesto activo de la empresa"));
    }

    /**
     * @param id producto
     * @return producto general con variantes
     * @throws NoEncontradoException si no existe o no agrupa variantes
     */
    private Producto general(Long id) {
        return productoRepository.findWithRelacionesById(id).filter(Producto::isEsAgrupador)
                .orElseThrow(() -> new NoEncontradoException("Producto con variantes", id));
    }

    /**
     * @param general   producto general
     * @param atributos sus atributos
     * @param variantes sus variantes
     * @return respuesta con el stock de cada variante y la suma en el general
     */
    private VariantesResponse respuesta(Producto general, List<AtributoVariante> atributos, List<Producto> variantes) {
        Map<Long, BigDecimal> totales = variantes.isEmpty() ? Map.of()
                : stockRepository.totalesDe(variantes.stream().map(Producto::getId).toList()).stream()
                        .collect(Collectors.toMap(TotalPorProducto::getProductoId, TotalPorProducto::getTotal));
        BigDecimal cero = BigDecimal.ZERO.setScale(4);
        BigDecimal suma = totales.values().stream().reduce(cero, BigDecimal::add);
        return new VariantesResponse(ProductoResponse.of(general, suma), atributos,
                variantes.stream().map(v -> ProductoResponse.of(v, totales.getOrDefault(v.getId(), cero))).toList());
    }

    /**
     * @param codigo código recibido
     * @return sin espacios y en mayúsculas
     */
    private static String codigo(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * @param barras código de barras recibido
     * @return sin espacios, o {@code null} si vino vacío
     */
    private static String barras(String barras) {
        return barras == null || barras.isBlank() ? null : barras.trim();
    }
}
