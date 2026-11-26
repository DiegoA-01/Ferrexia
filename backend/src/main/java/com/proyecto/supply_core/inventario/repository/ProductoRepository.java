package com.proyecto.supply_core.inventario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.inventario.entity.Producto;

/** Acceso a la tabla {@code producto}. Las consultas traen categoría y unidad. */
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Indica si el código ya está registrado.
     *
     * @param codigo código en mayúsculas
     * @return {@code true} si existe
     */
    boolean existsByCodigoIgnoreCase(String codigo);

    /**
     * Busca un producto por código sin distinguir mayúsculas, con categoría y unidad (carga masiva).
     *
     * @param codigo código del producto
     * @return producto, si existe
     */
    @EntityGraph(attributePaths = { "categoria", "unidadMedida" })
    Optional<Producto> findByCodigoIgnoreCase(String codigo);

    /**
     * Indica si otro producto usa el código (ediciones).
     *
     * @param codigo código en mayúsculas
     * @param id     producto que se está editando
     * @return {@code true} si otro lo tiene
     */
    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);

    /**
     * Busca por id trayendo categoría y unidad.
     *
     * @param id id del producto
     * @return producto, si existe
     */
    @EntityGraph(attributePaths = { "categoria", "unidadMedida" })
    Optional<Producto> findWithRelacionesById(Long id);

    /**
     * Indica si el producto existe y está activo (validador {@code @ProductoActivo}).
     *
     * @param id id del producto
     * @return {@code true} si existe y está activo
     */
    boolean existsByIdAndActivoTrue(Long id);

    /**
     * Búsqueda paginada con filtros opcionales ({@code null} = no filtrar).
     *
     * @param texto       coincidencia parcial en código o nombre
     * @param categoriaId categoría
     * @param activo      estado
     * @param agrupadores {@code false} deja fuera los productos generales que agrupan variantes (spec 028) y los servicios
     *                    (spec 032): lo que se compra o se mueve
     * @param pageable    página y orden
     * @return página de productos
     */
    @EntityGraph(attributePaths = { "categoria", "unidadMedida" })
    @Query("""
            select p from Producto p
            where (:texto is null or lower(p.codigo) like lower(concat('%', :texto, '%'))
                                  or lower(p.nombre) like lower(concat('%', :texto, '%')))
              and (:categoriaId is null or p.categoria.id = :categoriaId)
              and (:activo is null or p.activo = :activo)
              and (:agrupadores is null or :agrupadores = true or (p.esAgrupador = false and p.esServicio = false))
            """)
    Page<Producto> buscar(@Param("texto") String texto, @Param("categoriaId") Long categoriaId,
            @Param("activo") Boolean activo, @Param("agrupadores") Boolean agrupadores, Pageable pageable);

    /**
     * Variantes de un producto general (spec 028), activas e inactivas.
     *
     * @param agrupadorId producto general
     * @return variantes por código, con categoría y unidad
     */
    @EntityGraph(attributePaths = { "categoria", "unidadMedida" })
    List<Producto> findByAgrupadorIdOrderByCodigo(Long agrupadorId);

    /**
     * Indica si algún producto de la empresa usa el código de barras (spec 028).
     *
     * @param codigoBarras código de barras
     * @return {@code true} si ya está en uso
     */
    boolean existsByCodigoBarras(String codigoBarras);

    /**
     * Productos activos cuyo stock total (todas las bodegas) es menor que su stock mínimo.
     *
     * @return productos con alerta de reposición, por código
     */
    @EntityGraph(attributePaths = { "categoria", "unidadMedida" })
    @Query("""
            select p from Producto p
            where p.activo = true
              and p.stockMinimo > (select coalesce(sum(s.cantidad), 0) from StockProductoBodega s
                                   where s.producto = p)
            order by p.codigo
            """)
    List<Producto> bajoStockMinimo();

    /**
     * Productos activos con stock máximo cuyo total entre bodegas lo supera (spec 026, D17).
     *
     * @return productos sobre el máximo, por código
     */
    @EntityGraph(attributePaths = { "categoria", "unidadMedida" })
    @Query("""
            select p from Producto p
            where p.activo = true and p.stockMaximo is not null
              and p.stockMaximo < (select coalesce(sum(s.cantidad), 0) from StockProductoBodega s
                                   where s.producto = p)
            order by p.codigo
            """)
    List<Producto> sobreStockMaximo();
}
