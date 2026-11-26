package com.proyecto.supply_core.inventario.entity;

import java.math.BigDecimal;

import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;

import org.hibernate.annotations.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Producto del catálogo (tabla {@code producto}, UC-02). El stock NO vive aquí: está por bodega
 * en {@link StockProductoBodega} y solo lo cambia {@code MovimientoInventarioService}.
 * Nunca se borra: se desactiva y conserva su historial.
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_producto_id", nullable = false)
    private CategoriaProducto categoria;

    /** Unidad en la que se lleva el stock y se expresan los movimientos. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_medida_id", nullable = false)
    private UnidadMedida unidadMedida;

    /** Único, en mayúsculas. */
    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /** Total entre todas las bodegas por debajo del cual se genera alerta (UC flujo paso 1). */
    @Column(name = "stock_minimo", nullable = false, precision = 18, scale = 4)
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    @Column(name = "stock_maximo", precision = 18, scale = 4)
    private BigDecimal stockMaximo;

    /** Precio de referencia (para detectar precios atípicos en cotizaciones). */
    @Column(name = "costo_referencia", precision = 18, scale = 4)
    private BigDecimal costoReferencia;

    /** Proveedor del costo de referencia (spec 026, V49); lo actualiza la recepción. */
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "proveedor_referencia_id")
    private com.proyecto.supply_core.proveedor.entity.Proveedor proveedorReferencia;

    /** Fecha del costo de referencia (spec 026, V49); la actualiza la recepción. */
    @Column(name = "costo_referencia_fecha")
    private java.time.LocalDate costoReferenciaFecha;

    /** Costo promedio ponderado en la unidad del producto; lo actualiza la recepción (spec 008, V36). */
    @Column(name = "costo_promedio", precision = 18, scale = 4)
    private BigDecimal costoPromedio;

    @Column(nullable = false)
    private boolean activo = true;

    /** Días hábiles que tarda en llegar un pedido de este producto; {@code null} = el valor de la empresa. */
    @Column(name = "plazo_entrega_dias")
    private Integer plazoEntregaDias;

    /** Días hábiles de colchón para este producto; {@code null} = el valor de la empresa. */
    @Column(name = "dias_seguridad")
    private Integer diasSeguridad;

    /** Código de barras para el lector del punto de venta (único en la empresa). */
    @Column(name = "codigo_barras", length = 50)
    private String codigoBarras;

    /** Impuesto de venta; {@code null} si aún no se definió. */
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "tarifa_impuesto_id")
    private com.proyecto.supply_core.venta.entity.TarifaImpuesto tarifaImpuesto;

    /** {@code false} para productos que solo se consumen internamente. */
    @Column(nullable = false)
    private boolean vendible = true;

    /** El precio de mostrador incluye el impuesto (costumbre en Colombia); Java separa base e impuesto. */
    @Column(name = "precio_incluye_impuesto", nullable = false)
    private boolean precioIncluyeImpuesto = true;

    /** {@code true} si es un servicio (por ejemplo el envío a domicilio): no tiene stock ni kardex (spec 032, V53). */
    @Column(name = "es_servicio", nullable = false)
    private boolean esServicio;

    /** {@code true} si solo agrupa variantes: no tiene stock, no se vende ni se compra (spec 028, V50). */
    @Column(name = "es_agrupador", nullable = false)
    private boolean esAgrupador;

    /** Agrupador del que esta variante forma parte; {@code null} si no es variante (spec 028). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agrupador_id")
    private Producto agrupador;

    /** Atributos del agrupador con sus valores, en JSON: {@code [{"nombre":"Medida","valores":["1\""]}]}. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "atributos")
    private String atributos;

    /** Valores de la variante por atributo, en JSON: {@code {"Medida":"1\""}}. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "combinacion")
    private String combinacion;

    /**
     * Un agrupador no entra a ninguna operación (inventario, compras, ventas, precios): se usa una de sus variantes.
     *
     * @throws com.proyecto.supply_core.common.exception.NegocioException {@code PRODUCTO_AGRUPADOR} si lo es
     */
    public void exigirNoAgrupador() {
        if (esAgrupador) {
            throw new com.proyecto.supply_core.common.exception.NegocioException("PRODUCTO_AGRUPADOR",
                    "El producto " + codigo + " agrupa variantes: elija una de sus variantes");
        }
    }

    /**
     * Un servicio no mueve stock: no entra a inventario, compras ni recepción (spec 032).
     *
     * @throws com.proyecto.supply_core.common.exception.NegocioException {@code PRODUCTO_SERVICIO} si lo es
     */
    public void exigirNoServicio() {
        if (esServicio) {
            throw new com.proyecto.supply_core.common.exception.NegocioException("PRODUCTO_SERVICIO",
                    "El producto " + codigo + " es un servicio: no tiene stock");
        }
    }

    /**
     * @return costo con que se mide el margen: el promedio ponderado, o el último de compra si aún no hay promedio
     */
    public BigDecimal costoVigente() {
        return costoPromedio != null ? costoPromedio : costoReferencia;
    }
}
