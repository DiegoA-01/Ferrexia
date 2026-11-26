package com.proyecto.supply_core.compra.enlace.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.proyecto.supply_core.security.jwt.JwtUtil;

/**
 * Arma y valida el texto del enlace de cotización: {@code {id}.{firma}}, con la firma HMAC de id, empresa y versión
 * (spec 031). La validación corre antes de saber la empresa (desde el {@code AuthFilter}), por eso lee el enlace con
 * JDBC por su id y compara la firma en tiempo constante.
 */
@Component
public class FirmaEnlace {

    /** Separa las claves derivadas del secreto del JWT. */
    private static final String PROPOSITO = "enlace-cotizacion";

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    /**
     * Enlace válido por su firma (falta revisar si está vigente, dentro de la empresa).
     *
     * @param id        id del enlace
     * @param empresaId empresa del enlace
     */
    public record EnlaceFirmado(Long id, Long empresaId) {
    }

    /**
     * @param jwtUtil firma con clave derivada
     * @param jdbc    lectura del enlace sin empresa
     */
    public FirmaEnlace(JwtUtil jwtUtil, JdbcTemplate jdbc) {
        this.jwtUtil = jwtUtil;
        this.jdbc = jdbc;
    }

    /**
     * Texto del enlace.
     *
     * @param id        id del enlace
     * @param empresaId empresa
     * @param version   versión actual
     * @return {@code {id}.{firma}}
     */
    public String token(Long id, Long empresaId, int version) {
        return id + "." + firma(id, empresaId, version);
    }

    /**
     * Valida la firma del texto recibido.
     *
     * @param token texto de la ruta
     * @return enlace firmado, o vacío si el texto es inválido, no existe o su firma no coincide
     */
    public Optional<EnlaceFirmado> validar(String token) {
        if (token == null || token.length() > 80) {
            return Optional.empty();
        }
        int punto = token.indexOf('.');
        if (punto <= 0 || punto == token.length() - 1) {
            return Optional.empty();
        }
        long id;
        try {
            id = Long.parseLong(token.substring(0, punto));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        List<Object[]> filas = jdbc.query("SELECT empresa_id, version FROM enlace_cotizacion WHERE id = ?",
                (rs, n) -> new Object[] { rs.getLong(1), rs.getInt(2) }, id);
        if (filas.isEmpty()) {
            return Optional.empty();
        }
        Long empresaId = (Long) filas.get(0)[0];
        int version = (Integer) filas.get(0)[1];
        byte[] esperada = firma(id, empresaId, version).getBytes(StandardCharsets.UTF_8);
        byte[] recibida = token.substring(punto + 1).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperada, recibida) ? Optional.of(new EnlaceFirmado(id, empresaId))
                : Optional.empty();
    }

    /**
     * Firma de id, empresa y versión.
     *
     * @param id        enlace
     * @param empresaId empresa
     * @param version   versión
     * @return firma Base64 URL
     */
    private String firma(Long id, Long empresaId, int version) {
        return jwtUtil.firmar(PROPOSITO, id + ":" + empresaId + ":" + version);
    }
}
