package com.proyecto.supply_core.domicilio.entity;

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

/** Un cambio de un domicilio: estado, asignación o cobro (tabla {@code domicilio_evento}, spec 032). No se edita. */
@Entity
@Table(name = "domicilio_evento")
@Getter
@Setter
@NoArgsConstructor
public class DomicilioEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "domicilio_id", nullable = false, updatable = false)
    private Long domicilioId;

    /** Estado que quedó, o {@code COBRADO} para el cobro de la contraentrega. */
    @Column(nullable = false, updatable = false, length = 20)
    private String estado;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(updatable = false, length = 300)
    private String nota;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}
