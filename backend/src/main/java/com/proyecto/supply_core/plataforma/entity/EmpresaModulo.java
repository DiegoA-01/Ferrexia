package com.proyecto.supply_core.plataforma.entity;

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
 * Módulo apagado para una empresa (tabla {@code empresa_modulo}). Desde la spec 024 (D11) no hay planes: una empresa
 * tiene todos los módulos del catálogo menos los que tienen aquí {@code habilitado = false}. Sin fila, el módulo está
 * habilitado; una fila con {@code true} (de antes de V46) vale lo mismo que no tenerla.
 */
@Entity
@Table(name = "empresa_modulo")
@Getter
@Setter
@NoArgsConstructor
public class EmpresaModulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "modulo_codigo", nullable = false, updatable = false, length = 40)
    private String moduloCodigo;

    @Column(nullable = false)
    private boolean habilitado;
}
