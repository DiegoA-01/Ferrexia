package com.proyecto.supply_core.usuario.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rol (tabla {@code rol}). Los de V2 son plantillas del sistema (spec 022, V43): reciben sus permisos en
 * {@code rol_permiso}, y el acceso a cada endpoint lo decide {@code @RequierePermiso}. Los códigos viajan en el JWT y
 * los usan las políticas de aprobación y algunas reglas de los servicios.
 */
@Entity
@Table(name = "rol")
@Getter
@Setter
@NoArgsConstructor
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña de un rol propio; {@code null} en las plantillas del sistema (spec 022). */
    @Column(name = "empresa_id", updatable = false)
    private Long empresaId;

    /** Código estable usado en el JWT (ADMIN_SISTEMA, COMPRADOR, ...). Único entre todas las empresas. */
    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 250)
    private String descripcion;

    /** Plantilla del sistema: de solo lectura para las empresas. */
    @Column(name = "es_plantilla", nullable = false)
    private boolean esPlantilla;

    @ManyToMany
    @JoinTable(name = "rol_permiso",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<Permiso> permisos = new HashSet<>();
}
