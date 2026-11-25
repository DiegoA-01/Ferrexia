package com.proyecto.supply_core.empresa.entity;

import java.math.BigDecimal;

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

/** Sede física de la empresa (tabla {@code sede}). Nunca se borra: se desactiva. */
@Entity
@Table(name = "sede")
@Getter
@Setter
@NoArgsConstructor
public class Sede {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 250)
    private String direccion;

    /** Enlace de Google Maps de la ubicación (spec 020); {@code null} si no se dio. */
    @Column(name = "enlace_mapa", length = 500)
    private String enlaceMapa;

    /** Coordenadas de antes de la spec 020: se conservan en la base, pero la API ya no las recibe ni las muestra. */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitud;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitud;

    @Column(nullable = false)
    private boolean activo = true;
}
