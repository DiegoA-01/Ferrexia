package com.proyecto.supply_core.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.venta.entity.VentaPagoCambio;

/** Historial de cambios del método de pago de las ventas (spec 029). */
public interface VentaPagoCambioRepository extends JpaRepository<VentaPagoCambio, Long> {

    /**
     * @param ventaId venta
     * @return sus cambios de pago, del más antiguo al más reciente
     */
    List<VentaPagoCambio> findByVentaIdOrderByIdAsc(Long ventaId);

    /**
     * @param ventaId venta
     * @return {@code true} si su pago se cambió alguna vez
     */
    boolean existsByVentaId(Long ventaId);
}
