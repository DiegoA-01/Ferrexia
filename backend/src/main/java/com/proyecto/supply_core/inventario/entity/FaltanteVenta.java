package com.proyecto.supply_core.inventario.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

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
 * Lo que se vendió sin existencia (tabla {@code venta_sin_existencia}, spec 030, D13). El stock nunca bajó de cero: la
 * diferencia queda aquí como {@code pendiente} y se descuenta sola cuando entra mercancía de ese producto a esa bodega.
 */
@Entity
@Table(name = "venta_sin_existencia")
@Getter
@Setter
@NoArgsConstructor
public class FaltanteVenta {

    /** Estado del faltante. */
    public enum Estado {
        /** Falta descontar algo. */
        PENDIENTE,
        /** Ya se descontó todo con mercancía que entró. */
        REGULARIZADA,
        /** La venta se anuló o se devolvió antes de descontarlo. */
        ANULADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "venta_id", nullable = false, updatable = false)
    private Long ventaId;

    @Column(name = "producto_id", nullable = false, updatable = false)
    private Long productoId;

    @Column(name = "bodega_id", nullable = false, updatable = false)
    private Long bodegaId;

    /** Quien vendió sin existencia. */
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    /** Lo que faltaba al vender, en la unidad del producto. */
    @Column(nullable = false, updatable = false, precision = 18, scale = 4)
    private BigDecimal cantidad;

    /** Lo que falta por descontar. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal pendiente;

    @Column(nullable = false, updatable = false, length = 200)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "regularizada_en")
    private LocalDateTime regularizadaEn;
}
