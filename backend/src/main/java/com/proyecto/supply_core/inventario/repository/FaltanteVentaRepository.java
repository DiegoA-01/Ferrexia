package com.proyecto.supply_core.inventario.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.inventario.entity.FaltanteVenta;

import jakarta.persistence.LockModeType;

/** Faltantes de ventas sin existencia (spec 030). */
public interface FaltanteVentaRepository extends JpaRepository<FaltanteVenta, Long> {

    /**
     * @param productoId producto
     * @param bodegaId   bodega
     * @return faltantes pendientes, del más viejo al más nuevo, bloqueados para descontarlos
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select f from FaltanteVenta f
            where f.productoId = :productoId and f.bodegaId = :bodegaId
              and f.estado = com.proyecto.supply_core.inventario.entity.FaltanteVenta.Estado.PENDIENTE
            order by f.id
            """)
    List<FaltanteVenta> pendientesBloqueados(@Param("productoId") Long productoId, @Param("bodegaId") Long bodegaId);

    /**
     * @param ventaId venta
     * @return sus faltantes, bloqueados (para anularla o recibir una devolución)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FaltanteVenta f where f.ventaId = :ventaId order by f.id")
    List<FaltanteVenta> deVentaBloqueados(@Param("ventaId") Long ventaId);

    /**
     * @param ventaId venta
     * @return sus faltantes
     */
    List<FaltanteVenta> findByVentaIdOrderById(Long ventaId);

    /**
     * Reporte de ventas sin existencia (filtros opcionales).
     *
     * @param desde      desde (inclusive) o {@code null}
     * @param hasta      hasta (exclusive) o {@code null}
     * @param productoId producto o {@code null}
     * @param estado     estado o {@code null}
     * @return faltantes, del más reciente al más viejo
     */
    @Query("""
            select f from FaltanteVenta f
            where (:desde is null or f.creadoEn >= :desde) and (:hasta is null or f.creadoEn < :hasta)
              and (:productoId is null or f.productoId = :productoId) and (:estado is null or f.estado = :estado)
            order by f.id desc
            """)
    List<FaltanteVenta> reporte(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
            @Param("productoId") Long productoId, @Param("estado") FaltanteVenta.Estado estado);
}
