package com.proyecto.supply_core.caja.entity;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persona que puede abrir turno en una caja (tabla {@code caja_usuario}, V47, spec 025). Una persona sin ninguna fila
 * puede usar todas las cajas activas.
 */
@Entity
@Table(name = "caja_usuario")
@Getter
@Setter
@NoArgsConstructor
public class CajaUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "caja_id", nullable = false, updatable = false)
    private Long cajaId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;
}
