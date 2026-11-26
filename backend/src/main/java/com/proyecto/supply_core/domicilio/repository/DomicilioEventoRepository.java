package com.proyecto.supply_core.domicilio.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.domicilio.entity.DomicilioEvento;

/** Historial de los domicilios (spec 032). */
public interface DomicilioEventoRepository extends JpaRepository<DomicilioEvento, Long> {

    /**
     * @param domicilioId domicilio
     * @return sus cambios, del más viejo al más reciente
     */
    List<DomicilioEvento> findByDomicilioIdOrderByIdAsc(Long domicilioId);
}
