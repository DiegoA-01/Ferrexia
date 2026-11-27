package com.proyecto.supply_core.grupo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Super administrador de un grupo (tabla {@code usuario_grupo}). No pertenece a ninguna empresa: entra por su propio
 * login con MFA obligatorio y, para trabajar dentro de una empresa, usa el usuario Dueño vinculado en ella. Nunca se
 * borra: se desactiva.
 */
@Entity
@Table(name = "usuario_grupo")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioGrupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grupo_id", nullable = false, updatable = false)
    private Long grupoId;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Siempre en minúsculas y sin espacios; único en toda la plataforma. */
    @Column(nullable = false, length = 150)
    private String correo;

    @Column(name = "hash_password", nullable = false, length = 100)
    private String hashPassword;

    @Column(nullable = false)
    private boolean activo = true;

    /** Sube al desactivar la cuenta: el JWT lleva la versión con la que se emitió y deja de valer si ya no coincide. */
    @Column(name = "version_sesion", nullable = false)
    private int versionSesion;

    /** Secreto TOTP en base32. Dato sensible: solo sale por la API en el primer ingreso para configurarlo. */
    @Column(name = "mfa_secreto", length = 64)
    private String mfaSecreto;

    /** {@code true} cuando ya confirmó un código con ese secreto. */
    @Column(name = "mfa_activo", nullable = false)
    private boolean mfaActivo;

    /** Último intervalo de 30 s aceptado, para que un código no se use dos veces. */
    @Column(name = "mfa_ultimo_paso")
    private Long mfaUltimoPaso;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
