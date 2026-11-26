package com.proyecto.supply_core.venta.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

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
 * Un cambio del método de pago de una venta (tabla {@code venta_pago_cambio}, spec 029, D16). La venta guarda sus pagos
 * vigentes; aquí queda el antes y el después, quién lo hizo, quién lo autorizó y en qué turno quedó el ajuste de caja.
 * No se edita ni se borra.
 */
@Entity
@Table(name = "venta_pago_cambio")
@Getter
@Setter
@NoArgsConstructor
public class VentaPagoCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "venta_id", nullable = false, updatable = false)
    private Long ventaId;

    /** Turno donde quedó el ajuste de caja. */
    @Column(name = "turno_id", nullable = false, updatable = false)
    private Long turnoId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    /** Supervisor que dio su PIN; {@code null} si quien cambió tenía el permiso. */
    @Column(name = "autorizado_por_usuario_id", updatable = false)
    private Long autorizadoPorUsuarioId;

    /** Pagos antes del cambio, en JSON: {@code [{"medio":"EFECTIVO","monto":50000.00}]}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String antes;

    /** Pagos después del cambio, en el mismo formato. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String despues;

    @Column(nullable = false, updatable = false, length = 300)
    private String motivo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}
