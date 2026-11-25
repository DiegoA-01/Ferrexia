package com.proyecto.supply_core.security.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Permisos efectivos de una persona (spec 022): los de sus roles, más los que se le sumaron a mano, menos los que se le
 * quitaron. Los roles salen del JWT; sus permisos ({@code rol_permiso}) y los sueltos de la persona
 * ({@code usuario_permiso}, filtrados por la empresa de la sesión) se recuerdan unos segundos
 * ({@code app.permisos.cache-segundos}) para no ir a la base en cada petición.
 * <p>Se consulta con SQL directo porque corre en el interceptor, antes del servicio; por eso la consulta de permisos
 * sueltos lleva la empresa a mano.</p>
 * <p>Las reglas de los servicios preguntan con {@link #sesionTiene(String)} y {@link #usuarioTiene(UsuarioAutenticado,
 * String)}, sin inyectar este bean (muchas viven en métodos estáticos). En las pruebas sin Spring se usa
 * {@link #usarRespaldo(BiPredicate)}.</p>
 */
@Service
public class PermisosService {

    /** Lo recordado y cuándo vence. */
    private record Entrada<T>(T valor, long venceEn) {
    }

    /** El bean de la aplicación (lo fija {@link #registrar()}). */
    private static volatile PermisosService activo;
    /** Para pruebas sin Spring: decide con los roles de la persona. */
    private static volatile BiPredicate<Set<String>, String> respaldo;

    private final JdbcTemplate jdbc;
    private final long vigenciaMs;
    private volatile Entrada<Map<String, Set<String>>> porRol;
    private final Map<String, Entrada<Map<String, Boolean>>> sueltos = new ConcurrentHashMap<>();

    /**
     * @param jdbc          consultas de permisos
     * @param cacheSegundos segundos que se recuerdan los permisos
     */
    public PermisosService(JdbcTemplate jdbc, @Value("${app.permisos.cache-segundos:30}") long cacheSegundos) {
        this.jdbc = jdbc;
        this.vigenciaMs = cacheSegundos * 1000;
    }

    /** Spring lo llama al crear el bean: desde ahí las reglas de los servicios preguntan aquí (no al crearlo a mano). */
    @jakarta.annotation.PostConstruct
    void registrar() {
        activo = this;
    }

    /**
     * @param permiso código del permiso
     * @return {@code true} si la persona de la sesión lo tiene
     */
    public static boolean sesionTiene(String permiso) {
        return usuarioTiene(SesionActual.usuario(), permiso);
    }

    /**
     * @param usuario persona (de la sesión o armada desde la base)
     * @param permiso código del permiso
     * @return {@code true} si la persona lo tiene
     * @throws IllegalStateException si no hay bean ni respaldo (prueba sin configurar)
     */
    public static boolean usuarioTiene(UsuarioAutenticado usuario, String permiso) {
        PermisosService p = activo;
        if (p != null) {
            return p.tiene(usuario, permiso);
        }
        BiPredicate<Set<String>, String> r = respaldo;
        if (r == null) {
            throw new IllegalStateException("PermisosService no está disponible");
        }
        return r.test(usuario.roles(), permiso);
    }

    /**
     * Solo para pruebas sin Spring: decide los permisos con los roles.
     *
     * @param decision qué permisos da cada conjunto de roles
     */
    public static void usarRespaldo(BiPredicate<Set<String>, String> decision) {
        respaldo = decision;
    }

    /**
     * @param usuario sesión
     * @param permiso código del permiso
     * @return {@code true} si la persona tiene el permiso
     */
    public boolean tiene(UsuarioAutenticado usuario, String permiso) {
        Boolean manual = sueltos(usuario).get(permiso);
        if (manual != null) {
            return manual;
        }
        Map<String, Set<String>> mapa = porRol();
        for (String rol : usuario.roles()) {
            if (mapa.getOrDefault(rol, Set.of()).contains(permiso)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param usuario sesión
     * @return todos sus permisos efectivos
     */
    public Set<String> efectivos(UsuarioAutenticado usuario) {
        Set<String> todos = new HashSet<>();
        Map<String, Set<String>> mapa = porRol();
        usuario.roles().forEach(r -> todos.addAll(mapa.getOrDefault(r, Set.of())));
        sueltos(usuario).forEach((permiso, concedido) -> {
            if (concedido) {
                todos.add(permiso);
            } else {
                todos.remove(permiso);
            }
        });
        return todos;
    }

    /** Olvida lo recordado (al cambiar roles o permisos). */
    public void limpiar() {
        porRol = null;
        sueltos.clear();
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * @return permisos por código de rol
     */
    private Map<String, Set<String>> porRol() {
        Entrada<Map<String, Set<String>>> e = porRol;
        long ahora = System.currentTimeMillis();
        if (e == null || e.venceEn() < ahora) {
            Map<String, Set<String>> mapa = new HashMap<>();
            jdbc.query("SELECT r.codigo, p.codigo FROM rol_permiso rp JOIN rol r ON r.id = rp.rol_id "
                    + "JOIN permiso p ON p.id = rp.permiso_id",
                    rs -> {
                        mapa.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2));
                    });
            e = new Entrada<>(Map.copyOf(mapa), ahora + vigenciaMs);
            porRol = e;
        }
        return e.valor();
    }

    /**
     * @param usuario sesión
     * @return permisos sumados ({@code true}) o quitados ({@code false}) a mano a la persona, solo de su empresa
     */
    private Map<String, Boolean> sueltos(UsuarioAutenticado usuario) {
        String clave = usuario.empresaId() + ":" + usuario.id();
        long ahora = System.currentTimeMillis();
        Entrada<Map<String, Boolean>> e = sueltos.get(clave);
        if (e == null || e.venceEn() < ahora) {
            Map<String, Boolean> mapa = new HashMap<>();
            jdbc.query("SELECT p.codigo, up.concedido FROM usuario_permiso up JOIN permiso p ON p.id = up.permiso_id "
                    + "WHERE up.usuario_id = ? AND up.empresa_id = ?",
                    rs -> {
                        mapa.put(rs.getString(1), rs.getBoolean(2));
                    }, usuario.id(), usuario.empresaId());
            e = new Entrada<>(Map.copyOf(mapa), ahora + vigenciaMs);
            sueltos.put(clave, e);
        }
        return e.valor();
    }
}
