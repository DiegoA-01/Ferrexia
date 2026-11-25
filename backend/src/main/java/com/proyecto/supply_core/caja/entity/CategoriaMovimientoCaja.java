package com.proyecto.supply_core.caja.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Categoría de un ingreso o egreso de caja (tabla {@code categoria_movimiento_caja}, V42, spec 021). La crea cada
 * empresa; no se borra, se desactiva.
 */
@Entity
@Table(name = "categoria_movimiento_caja")
@Getter
@Setter
@NoArgsConstructor
public class CategoriaMovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private TipoCategoriaMovimiento tipo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private LocalDateTime creadoEn;
}
