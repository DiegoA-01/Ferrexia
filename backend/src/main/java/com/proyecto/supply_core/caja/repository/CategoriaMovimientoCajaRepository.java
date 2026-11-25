package com.proyecto.supply_core.caja.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.caja.entity.CategoriaMovimientoCaja;
import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;

/** Categorías de ingresos y egresos de caja de la empresa ({@code @TenantId}). */
public interface CategoriaMovimientoCajaRepository extends JpaRepository<CategoriaMovimientoCaja, Long> {

    /**
     * @param tipo   filtro por tipo ({@code null} = los dos)
     * @param activa filtro por estado ({@code null} = todas)
     * @return categorías por tipo y nombre
     */
    @Query("""
            select c from CategoriaMovimientoCaja c
            where (:tipo is null or c.tipo = :tipo) and (:activa is null or c.activa = :activa)
            order by c.tipo, c.nombre
            """)
    List<CategoriaMovimientoCaja> listar(@Param("tipo") TipoCategoriaMovimiento tipo, @Param("activa") Boolean activa);

    /**
     * @param tipo   tipo
     * @param nombre nombre
     * @param id     categoría que se edita (o -1 al crear)
     * @return {@code true} si otra categoría del mismo tipo ya usa ese nombre
     */
    boolean existsByTipoAndNombreIgnoreCaseAndIdNot(TipoCategoriaMovimiento tipo, String nombre, Long id);

    /**
     * @return {@code true} si la empresa ya tiene alguna categoría
     */
    @Query("select count(c) > 0 from CategoriaMovimientoCaja c")
    boolean hayAlguna();
}
