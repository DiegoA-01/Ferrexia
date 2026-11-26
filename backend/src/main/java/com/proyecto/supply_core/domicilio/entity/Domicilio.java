package com.proyecto.supply_core.domicilio.entity;

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
 * Domicilio de una venta (tabla {@code domicilio}, spec 032). La venta se confirma al despachar; si es contraentrega,
 * el dinero llega a la caja cuando un cajero lo cobra ({@code cobradoEn}).
 */
@Entity
@Table(name = "domicilio")
@Getter
@Setter
@NoArgsConstructor
public class Domicilio {

    /** Cómo se paga. */
    public enum Pago {
        /** Se pagó al vender: el dinero ya está en la caja. */
        PAGADO,
        /** Se paga al entregar: lo cobra un cajero cuando vuelve el domiciliario. */
        CONTRAENTREGA
    }

    /** Estado del domicilio. */
    public enum Estado {
        /** Recién vendido, sin domiciliario. */
        PENDIENTE,
        /** Con domiciliario, sin salir. */
        ASIGNADO,
        /** Salió con el domiciliario. */
        EN_CAMINO,
        /** Entregado al cliente. */
        ENTREGADO,
        /** Volvió sin entregar: la venta se anula y el stock regresa. */
        DEVUELTO,
        /** Se canceló antes de salir: la venta se anula. */
        CANCELADO;

        /**
         * @return {@code true} si ya no cambia
         */
        public boolean terminal() {
            return this == ENTREGADO || this == DEVUELTO || this == CANCELADO;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(nullable = false, updatable = false, length = 20)
    private String consecutivo;

    @Column(name = "venta_id", nullable = false, updatable = false)
    private Long ventaId;

    @Column(nullable = false, length = 300)
    private String direccion;

    @Column(nullable = false, length = 30)
    private String telefono;

    @Column(length = 300)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Pago pago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.PENDIENTE;

    /** Domiciliario interno (usuario con el permiso de repartir), o {@code null}. */
    @Column(name = "domiciliario_usuario_id")
    private Long domiciliarioUsuarioId;

    /** Nombre del domiciliario externo, o {@code null}. */
    @Column(name = "domiciliario_nombre", length = 150)
    private String domiciliarioNombre;

    /** Teléfono del domiciliario externo, o {@code null}. */
    @Column(name = "domiciliario_telefono", length = 30)
    private String domiciliarioTelefono;

    /** Motivo de la devolución o cancelación. */
    @Column(length = 300)
    private String motivo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "entregado_en")
    private LocalDateTime entregadoEn;

    /** Cuándo se cobró la contraentrega en la caja. */
    @Column(name = "cobrado_en")
    private LocalDateTime cobradoEn;

    @Column(name = "cobrado_turno_id")
    private Long cobradoTurnoId;

    @Column(name = "cobrado_por_usuario_id")
    private Long cobradoPorUsuarioId;
}
