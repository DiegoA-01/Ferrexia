package com.proyecto.supply_core.domicilio.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.domicilio.entity.Domicilio;

import jakarta.persistence.LockModeType;

/** Domicilios (spec 032). */
public interface DomicilioRepository extends JpaRepository<Domicilio, Long> {

    /**
     * @param id domicilio
     * @return el domicilio bloqueado para cambiarlo sin carreras
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Domicilio d where d.id = :id")
    Optional<Domicilio> bloquear(@Param("id") Long id);

    /**
     * @param ventaId venta
     * @return su domicilio, si tiene
     */
    Optional<Domicilio> findByVentaId(Long ventaId);

    /**
     * @param desde          desde (inclusive)
     * @param hasta          hasta (exclusive)
     * @param estado         estado o {@code null}
     * @param domiciliarioId solo los de este domiciliario, o {@code null}
     * @return domicilios del periodo, del más reciente al más viejo
     */
    @Query("""
            select d from Domicilio d
            where d.creadoEn >= :desde and d.creadoEn < :hasta
              and (:estado is null or d.estado = :estado)
              and (:domiciliarioId is null or d.domiciliarioUsuarioId = :domiciliarioId)
            order by d.id desc
            """)
    List<Domicilio> buscar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
            @Param("estado") Domicilio.Estado estado, @Param("domiciliarioId") Long domiciliarioId);
}
