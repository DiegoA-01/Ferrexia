package com.proyecto.supply_core.compra.enlace.entity;

import java.time.LocalDateTime;

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
 * Enlace de un solo uso para que un proveedor invitado cotice una RFQ sin cuenta (tabla {@code enlace_cotizacion},
 * spec 031). El enlace es {@code {id}.{firma}}: la firma se recalcula con la versión, así que aquí no queda nada que
 * sirva para armarlo. Regenerar sube la versión.
 */
@Entity
@Table(name = "enlace_cotizacion")
@Getter
@Setter
@NoArgsConstructor
public class EnlaceCotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "rfq_id", nullable = false, updatable = false)
    private Long rfqId;

    @Column(name = "proveedor_id", nullable = false, updatable = false)
    private Long proveedorId;

    /** Sube al regenerar: el enlace con la versión anterior deja de servir. */
    @Column(nullable = false)
    private int version = 1;

    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn;

    /** Cuándo se envió la cotización desde el enlace (queda cerrado). */
    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    @Column(name = "creado_por_usuario_id", updatable = false)
    private Long creadoPorUsuarioId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}
