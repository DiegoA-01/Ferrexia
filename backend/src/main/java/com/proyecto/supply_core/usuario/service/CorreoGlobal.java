package com.proyecto.supply_core.usuario.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Consulta del correo de los usuarios en todas las empresas a la vez (spec 037). Va por JDBC con SQL explícito porque
 * la entidad {@code Usuario} filtra por empresa; solo lee el id del usuario y su empresa. Mira la columna
 * {@code correo_login} (V58), que no incluye al usuario interno del super administrador de un grupo: ese repite su
 * correo en cada empresa y nunca entra por el login normal.
 */
@Component
public class CorreoGlobal {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc acceso SQL directo
     */
    public CorreoGlobal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Empresa a la que pertenece el correo de ingreso.
     *
     * @param correo correo escrito por la persona (se normaliza)
     * @return id de la empresa, o vacío si ningún usuario de empresa lo tiene
     */
    public Optional<Long> empresaDe(String correo) {
        List<Long> filas = jdbc.queryForList("SELECT empresa_id FROM usuario WHERE correo_login = ?", Long.class,
                normalizar(correo));
        return filas.stream().findFirst();
    }

    /**
     * Indica si otro usuario de cualquier empresa ya usa el correo.
     *
     * @param correo    correo a revisar (se normaliza)
     * @param excluirId usuario que se está editando (no cuenta contra sí mismo), o {@code null} en un alta
     * @return {@code true} si ya está en uso
     */
    public boolean enUso(String correo, Long excluirId) {
        Long cuenta = jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE correo_login = ? AND id <> ?",
                Long.class, normalizar(correo), excluirId == null ? -1L : excluirId);
        return cuenta != null && cuenta > 0;
    }

    /**
     * Empresa dueña del token de recuperación de clave (el token no lleva la empresa).
     *
     * @param tokenHash hash del token recibido
     * @return id de la empresa, o vacío si no existe
     */
    public Optional<Long> empresaDeRecuperacion(String tokenHash) {
        return jdbc.queryForList("SELECT empresa_id FROM recuperacion_password WHERE token_hash = ?", Long.class,
                tokenHash).stream().findFirst();
    }

    /**
     * Normaliza el correo como se guarda.
     *
     * @param correo correo escrito
     * @return en minúsculas y sin espacios en los extremos
     */
    private static String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }
}
