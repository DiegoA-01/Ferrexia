package com.proyecto.supply_core.caja.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.caja.entity.CajaUsuario;

/** Cajas asignadas a cada persona ({@code @TenantId}, spec 025). */
public interface CajaUsuarioRepository extends JpaRepository<CajaUsuario, Long> {

    /**
     * @param usuarioId persona
     * @return sus cajas asignadas
     */
    List<CajaUsuario> findByUsuarioId(Long usuarioId);

    /**
     * @param cajaId caja
     * @return quiénes la pueden usar
     */
    List<CajaUsuario> findByCajaId(Long cajaId);
}
