package com.proyecto.supply_core.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee de la migración V43 qué permisos recibe cada rol del sistema (spec 022). Lo usan las pruebas que no tienen base de
 * datos ({@code @WebMvcTest}) para que el acceso sea el mismo que en producción.
 */
public final class PermisosDeV43 {

    private static final String MIGRACION = "/db/migration/V43__permisos_y_plantillas.sql";
    private static final Pattern PAR = Pattern.compile("\\('([A-Z_]+)', '([A-Z_]+)'\\)");
    private static Map<String, Set<String>> porRol;

    /** Clase de utilidades: no se instancia. */
    private PermisosDeV43() {
    }

    /**
     * @return permisos por código de rol, tal como los siembra V43
     */
    public static synchronized Map<String, Set<String>> porRol() {
        if (porRol == null) {
            try (InputStream in = PermisosDeV43.class.getResourceAsStream(MIGRACION)) {
                String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                String pares = sql.substring(sql.indexOf("INSERT INTO rol_permiso"), sql.indexOf("CREATE TABLE usuario_permiso"));
                Map<String, Set<String>> mapa = new HashMap<>();
                Matcher m = PAR.matcher(pares);
                while (m.find()) {
                    mapa.computeIfAbsent(m.group(1), k -> new HashSet<>()).add(m.group(2));
                }
                porRol = Map.copyOf(mapa);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer " + MIGRACION, e);
            }
        }
        return porRol;
    }

    /**
     * @param roles   roles de la persona
     * @param permiso permiso pedido
     * @return {@code true} si alguno de sus roles lo da
     */
    public static boolean tiene(Set<String> roles, String permiso) {
        return roles.stream().anyMatch(r -> porRol().getOrDefault(r, Set.of()).contains(permiso));
    }
}
