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
 * Lee de las migraciones V43, V44 y V47 qué permisos recibe cada plantilla del sistema (spec 022). Lo usan las pruebas que no
 * tienen base de datos ({@code @WebMvcTest} y las de servicios con Mockito) para que el acceso sea el mismo que en
 * producción.
 */
public final class PermisosDeMigraciones {

    private static final String[] MIGRACIONES = { "/db/migration/V43__permisos_y_plantillas.sql",
        "/db/migration/V44__roles_propios_y_clave_corta.sql", "/db/migration/V47__caja_con_permisos.sql", "/db/migration/V48__dueno_cotiza.sql" };
    private static final Pattern PAR = Pattern.compile("\\('([A-Z_]+)', '([A-Z_]+)'\\)");
    private static Map<String, Set<String>> porRol;

    /** Clase de utilidades: no se instancia. */
    private PermisosDeMigraciones() {
    }

    /**
     * @return permisos por código de rol, tal como los siembran las migraciones
     */
    public static synchronized Map<String, Set<String>> porRol() {
        if (porRol == null) {
            Map<String, Set<String>> mapa = new HashMap<>();
            for (String migracion : MIGRACIONES) {
                String sql = leer(migracion);
                int desde = sql.indexOf("INSERT INTO rol_permiso");
                String pares = sql.substring(desde, sql.indexOf(");", desde));
                Matcher m = PAR.matcher(pares);
                while (m.find()) {
                    mapa.computeIfAbsent(m.group(1), k -> new HashSet<>()).add(m.group(2));
                }
            }
            // V44: Administrador = Dueño sin editar los datos de la empresa
            Set<String> administrador = new HashSet<>(mapa.get("ADMIN_SISTEMA"));
            administrador.remove("EMPRESA_GESTIONAR");
            mapa.put("ADMINISTRADOR", administrador);
            porRol = Map.copyOf(mapa);
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

    /**
     * @param ruta recurso
     * @return contenido
     */
    private static String leer(String ruta) {
        try (InputStream in = PermisosDeMigraciones.class.getResourceAsStream(ruta)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ruta, e);
        }
    }
}
