package com.proyecto.supply_core.compra.enlace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.compra.enlace.entity.EnlaceCotizacion;

import jakarta.persistence.LockModeType;

/** Enlaces de cotización de la empresa del contexto (spec 031). */
public interface EnlaceCotizacionRepository extends JpaRepository<EnlaceCotizacion, Long> {

    /**
     * Enlaces de una RFQ.
     *
     * @param rfqId RFQ
     * @return enlaces en orden de creación
     */
    List<EnlaceCotizacion> findByRfqIdOrderByIdAsc(Long rfqId);

    /**
     * Enlace de un proveedor en una RFQ.
     *
     * @param rfqId       RFQ
     * @param proveedorId proveedor
     * @return enlace, si existe
     */
    Optional<EnlaceCotizacion> findByRfqIdAndProveedorId(Long rfqId, Long proveedorId);

    /**
     * Bloquea el enlace para enviar la cotización una sola vez.
     *
     * @param id enlace
     * @return enlace bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EnlaceCotizacion e where e.id = :id")
    Optional<EnlaceCotizacion> bloquear(@Param("id") Long id);
}
