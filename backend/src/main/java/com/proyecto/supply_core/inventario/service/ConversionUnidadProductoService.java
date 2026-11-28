package com.proyecto.supply_core.inventario.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.common.exception.NegocioException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Spec 038: cambia la unidad de un producto que ya tiene movimientos, solo a otra unidad de la misma medida (por ejemplo
 * centímetros a metros). Todo lo que está guardado "en la unidad del producto" se convierte en la misma transacción,
 * para que el stock, el kárdex, los costos y los documentos sigan cuadrando:
 * <ul>
 *   <li>cantidades × factor: stock por bodega, movimientos (cantidad y stock resultante), mínimo y máximo por sede,
 *       faltantes de venta sin existencia, líneas de venta y devolución, despachos, órdenes de compra, recepciones,
 *       topes de contrato y pedidos internos;</li>
 *   <li>precios ÷ factor: costo de los movimientos y de las líneas de venta, precio de las órdenes y de los contratos.</li>
 * </ul>
 * <p>Lo que guarda su propia unidad (solicitudes, RFQ, pedidos de venta, precios de venta) no cambia: sigue
 * convirtiéndose al usarse. El producto mismo (mínimo, máximo y costos) lo convierte {@link ProductoService}.</p>
 */
@Service
public class ConversionUnidadProductoService {

    private static final Logger log = LoggerFactory.getLogger(ConversionUnidadProductoService.class);

    /** Sentencias que multiplican cantidades ({@code :k}) y dividen precios, por producto ({@code :id}). */
    private static final List<String> SENTENCIAS = List.of(
            "UPDATE stock_producto_bodega SET cantidad = ROUND(cantidad * :k, 4) WHERE producto_id = :id",
            "UPDATE movimiento_inventario SET cantidad = ROUND(cantidad * :k, 4), "
                    + "stock_resultante = ROUND(stock_resultante * :k, 4), "
                    + "stock_resultante_destino = ROUND(stock_resultante_destino * :k, 4), "
                    + "costo_unitario = ROUND(costo_unitario / :k, 4) WHERE producto_id = :id",
            "UPDATE producto_sede SET stock_minimo = ROUND(stock_minimo * :k, 4), "
                    + "stock_maximo = ROUND(stock_maximo * :k, 4) WHERE producto_id = :id",
            "UPDATE venta_sin_existencia SET cantidad = ROUND(cantidad * :k, 4), pendiente = ROUND(pendiente * :k, 4) "
                    + "WHERE producto_id = :id",
            "UPDATE devolucion_venta_linea d JOIN venta_linea v ON v.id = d.venta_linea_id "
                    + "SET d.cantidad_unidad_producto = ROUND(d.cantidad_unidad_producto * :k, 4) WHERE v.producto_id = :id",
            "UPDATE venta_linea SET cantidad_unidad_producto = ROUND(cantidad_unidad_producto * :k, 4), "
                    + "costo_unitario = ROUND(costo_unitario / :k, 4) WHERE producto_id = :id",
            "UPDATE despacho_linea SET cantidad_base = ROUND(cantidad_base * :k, 4) WHERE producto_id = :id",
            "UPDATE recepcion_linea r JOIN orden_compra_linea o ON o.id = r.orden_compra_linea_id "
                    + "SET r.cantidad_recibida = ROUND(r.cantidad_recibida * :k, 4), "
                    + "r.cantidad_aceptada = ROUND(r.cantidad_aceptada * :k, 4), "
                    + "r.cantidad_rechazada = ROUND(r.cantidad_rechazada * :k, 4), "
                    + "r.cantidad_faltante = ROUND(r.cantidad_faltante * :k, 4), "
                    + "r.cantidad_excedente = ROUND(r.cantidad_excedente * :k, 4) WHERE o.producto_id = :id",
            "UPDATE orden_compra_linea SET cantidad = ROUND(cantidad * :k, 4), "
                    + "precio_unitario = ROUND(precio_unitario / :k, 4) WHERE producto_id = :id",
            "UPDATE contrato_precio SET precio_unitario = ROUND(precio_unitario / :k, 4), "
                    + "cantidad_maxima = ROUND(cantidad_maxima * :k, 4) WHERE producto_id = :id",
            "UPDATE pedido_interno SET cantidad = ROUND(cantidad * :k, 4) WHERE producto_id = :id");

    @PersistenceContext
    private EntityManager em;

    /**
     * Factor que pasa una cantidad de la unidad vieja a la nueva (cantidad nueva = cantidad vieja × factor).
     *
     * @param antes   unidad actual del producto
     * @param despues unidad nueva
     * @return factor (por ejemplo 0,01 de centímetros a metros)
     * @throws NegocioException {@code UNIDAD_EN_USO} si no miden lo mismo
     */
    public static BigDecimal factor(UnidadMedida antes, UnidadMedida despues) {
        return factor(antes, despues, null);
    }

    /**
     * Factor de la conversión. Si las dos unidades miden lo mismo sale de sus factores; si miden otra cosa (unidades a
     * metros), lo da quien edita: cuántas unidades nuevas equivale 1 unidad actual.
     *
     * @param antes        unidad actual del producto
     * @param despues      unidad nueva
     * @param equivalencia 1 unidad actual = {@code equivalencia} unidades nuevas; solo se usa si miden otra cosa
     * @return cantidad nueva = cantidad vieja × factor
     * @throws NegocioException {@code UNIDAD_EN_USO} si miden otra cosa y no se dijo la equivalencia
     */
    public static BigDecimal factor(UnidadMedida antes, UnidadMedida despues, BigDecimal equivalencia) {
        if (antes.getMagnitud() != despues.getMagnitud()) {
            if (equivalencia == null || equivalencia.signum() <= 0) {
                throw new NegocioException("UNIDAD_EN_USO", "El producto ya tiene movimientos y " + antes.getCodigo()
                        + " mide " + antes.getMagnitud() + " mientras " + despues.getCodigo() + " mide "
                        + despues.getMagnitud() + ": indica cuántos " + despues.getCodigo() + " equivale 1 "
                        + antes.getCodigo() + " para convertir todo");
            }
            return equivalencia;
        }
        return antes.getFactorConversionBase().divide(despues.getFactorConversionBase(), 10, RoundingMode.HALF_UP);
    }

    /**
     * Pasa a la medida nueva lo que guarda su propia unidad de la medida vieja (spec 038, cambio de medida con
     * equivalencia), porque después ya no se podría convertir:
     * <ul>
     *   <li>precios de venta: los de la unidad del producto pasan a la nueva (precio ÷ factor); los de otras unidades de
     *       la medida vieja (la caja) dejan de estar vigentes;</li>
     *   <li>solicitudes de compra, pedidos de venta y despachos: la cantidad pasa a la unidad nueva.</li>
     * </ul>
     * <p>Las ventas ya hechas conservan la unidad en que se vendieron (su cantidad en la unidad del producto ya la
     * convirtió {@link #convertir}). No se permite si el producto está en una RFQ en curso.</p>
     *
     * @param productoId producto
     * @param antes      unidad vieja del producto
     * @param despues    unidad nueva
     * @param factor     cantidad nueva = cantidad vieja × factor
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cambiarMedida(Long productoId, UnidadMedida antes, UnidadMedida despues, BigDecimal factor) {
        if (((Number) em.createNativeQuery("SELECT COUNT(*) FROM rfq_linea l JOIN rfq r ON r.id = l.rfq_id "
                + "WHERE l.producto_id = :id AND r.estado IN ('BORRADOR', 'ABIERTA')").setParameter("id", productoId)
                .getSingleResult()).longValue() > 0) {
            throw new NegocioException("UNIDAD_EN_RFQ", "El producto está en una cotización (RFQ) en curso: ciérrela o "
                    + "cancélela antes de cambiarle la medida");
        }
        String magnitud = antes.getMagnitud().name();
        em.createNativeQuery("UPDATE precio_producto p JOIN unidad_medida u ON u.id = p.unidad_medida_id "
                        + "SET p.vigente_hasta = NOW() WHERE p.producto_id = :id AND p.unidad_medida_id <> :vieja "
                        + "AND u.magnitud = :mag AND p.vigente_hasta IS NULL")
                .setParameter("id", productoId).setParameter("vieja", antes.getId()).setParameter("mag", magnitud)
                .executeUpdate();
        em.createNativeQuery("UPDATE precio_producto SET precio = ROUND(precio / :k, 2), "
                        + "precio_minimo = ROUND(precio_minimo / :k, 2), unidad_medida_id = :nueva "
                        + "WHERE producto_id = :id AND unidad_medida_id = :vieja")
                .setParameter("k", factor).setParameter("nueva", despues.getId()).setParameter("id", productoId)
                .setParameter("vieja", antes.getId()).executeUpdate();
        for (String tabla : List.of("solicitud_compra_linea", "pedido_venta_linea", "despacho_linea")) {
            em.createNativeQuery("UPDATE " + tabla + " t JOIN unidad_medida u ON u.id = t.unidad_medida_id "
                            + "SET t.cantidad = ROUND(t.cantidad * u.factor_conversion_base / :fp * :k, 4), "
                            + "t.unidad_medida_id = :nueva WHERE t.producto_id = :id AND u.magnitud = :mag")
                    .setParameter("fp", antes.getFactorConversionBase()).setParameter("k", factor)
                    .setParameter("nueva", despues.getId()).setParameter("id", productoId).setParameter("mag", magnitud)
                    .executeUpdate();
        }
        log.info("Producto {}: medida cambiada de {} a {} con factor {}", productoId, antes.getCodigo(), despues.getCodigo(),
                factor.stripTrailingZeros().toPlainString());
    }

    /**
     * Convierte todo lo guardado en la unidad del producto. Corre dentro de la transacción de quien lo llama.
     *
     * @param productoId producto
     * @param factor     cantidad nueva = cantidad vieja × factor
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void convertir(Long productoId, BigDecimal factor) {
        em.flush(); // lo pendiente del producto se escribe antes de las sentencias directas
        if (((Number) em.createNativeQuery("SELECT COUNT(*) FROM traslado_grupo_linea "
                + "WHERE producto_origen_id = :id OR producto_destino_id = :id").setParameter("id", productoId)
                .getSingleResult()).longValue() > 0) {
            throw new NegocioException("UNIDAD_EN_TRASLADO", "El producto aparece en traslados entre empresas del grupo; "
                    + "su unidad no se puede cambiar porque la otra empresa lo registra en la unidad actual");
        }
        int filas = 0;
        for (String sql : SENTENCIAS) {
            filas += em.createNativeQuery(sql).setParameter("k", factor).setParameter("id", productoId).executeUpdate();
        }
        log.info("Unidad del producto {} cambiada: {} filas convertidas con factor {}", productoId, filas,
                factor.stripTrailingZeros().toPlainString());
    }
}
