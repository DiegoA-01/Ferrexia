package com.proyecto.supply_core.usuario.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.service.PermisosService;
import com.proyecto.supply_core.usuario.dto.AjustePermisosRequest;
import com.proyecto.supply_core.usuario.dto.PermisoUsuarioResponse;
import com.proyecto.supply_core.usuario.entity.Permiso;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.PermisoRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Permisos sueltos de una persona (spec 022): en su ficha se ve cada permiso con lo que dice su rol y lo sumado o
 * quitado a mano. Los ajustes viven en {@code usuario_permiso} con la empresa de la sesión (SQL directo, con la empresa
 * a mano). Nadie cambia sus propios permisos.
 */
@Service
public class UsuarioPermisoService {

    private final UsuarioRepository usuarioRepository;
    private final PermisoRepository permisoRepository;
    private final PermisosService permisosService;
    private final AuditoriaService auditoriaService;
    private final JdbcTemplate jdbc;

    /**
     * @param usuarioRepository personas de la empresa
     * @param permisoRepository catálogo de permisos
     * @param permisosService   se limpia al cambiar ajustes
     * @param auditoriaService  registro de cada cambio
     * @param jdbc              ajustes por persona
     */
    public UsuarioPermisoService(UsuarioRepository usuarioRepository, PermisoRepository permisoRepository,
            PermisosService permisosService, AuditoriaService auditoriaService, JdbcTemplate jdbc) {
        this.usuarioRepository = usuarioRepository;
        this.permisoRepository = permisoRepository;
        this.permisosService = permisosService;
        this.auditoriaService = auditoriaService;
        this.jdbc = jdbc;
    }

    /**
     * @param usuarioId persona de la empresa
     * @return todos los permisos del catálogo con lo que dice su rol, su ajuste y si al final lo tiene
     * @throws NoEncontradoException si no es de la empresa
     */
    @Transactional(readOnly = true)
    public List<PermisoUsuarioResponse> ver(Long usuarioId) {
        Usuario u = usuario(usuarioId);
        Set<String> delRol = delRol(u);
        Map<String, Boolean> ajustes = ajustes(u);
        return permisoRepository.findAllByOrderByModuloAscNombreAsc().stream()
                .filter(p -> !Permisos.PORTAL_PROVEEDOR.equals(p.getCodigo()) || delRol.contains(p.getCodigo()))
                .map(p -> {
                    Boolean ajuste = ajustes.get(p.getCodigo());
                    boolean rol = delRol.contains(p.getCodigo());
                    return new PermisoUsuarioResponse(p.getCodigo(), p.getNombre(), p.getModulo(), rol,
                            ajuste == null ? null : ajuste ? "SUMADO" : "QUITADO", ajuste == null ? rol : ajuste);
                })
                .toList();
    }

    /**
     * Reemplaza los ajustes de la persona. Un ajuste que no cambia nada (sumar lo que el rol ya da o quitar lo que no da)
     * se ignora.
     *
     * @param usuarioId persona de la empresa
     * @param req       ajustes
     * @return la ficha de permisos actualizada
     * @throws NoEncontradoException si no es de la empresa
     * @throws NegocioException      si son sus propios permisos o algún permiso no sirve
     */
    @Transactional
    public List<PermisoUsuarioResponse> cambiar(Long usuarioId, AjustePermisosRequest req) {
        if (usuarioId.equals(SesionActual.usuario().id())) {
            throw new NegocioException("AUTO_PERMISOS", "Nadie cambia sus propios permisos: pídaselo a otro administrador");
        }
        Usuario u = usuario(usuarioId);
        if (usuarioRepository.contarVinculosDeGrupo(usuarioId) > 0) {
            throw new NegocioException("USUARIO_DE_GRUPO",
                    "Este usuario lo administra el grupo de empresas desde la plataforma");
        }
        Set<String> delRol = delRol(u);
        Map<String, Boolean> pedidos = new TreeMap<>();
        req.ajustes().forEach(a -> pedidos.put(a.permiso().trim().toUpperCase(Locale.ROOT), a.concedido()));
        if (pedidos.containsKey(Permisos.PORTAL_PROVEEDOR)) {
            throw new NegocioException("PERMISO_NO_PERMITIDO", "El portal del proveedor no se da como permiso suelto");
        }
        Map<String, Long> ids = new HashMap<>();
        for (Permiso p : permisoRepository.findByCodigoIn(pedidos.keySet())) {
            ids.put(p.getCodigo(), p.getId());
        }
        if (ids.size() != pedidos.size()) {
            Set<String> faltan = new HashSet<>(pedidos.keySet());
            faltan.removeAll(ids.keySet());
            throw new NegocioException("PERMISO_INEXISTENTE", "No existen los permisos " + faltan);
        }
        Map<String, Boolean> antes = ajustes(u);
        jdbc.update("DELETE FROM usuario_permiso WHERE usuario_id = ? AND empresa_id = ?", u.getId(), u.getEmpresaId());
        Map<String, Boolean> guardados = new TreeMap<>();
        pedidos.forEach((codigo, concedido) -> {
            if (concedido != delRol.contains(codigo)) {
                jdbc.update("INSERT INTO usuario_permiso (usuario_id, permiso_id, empresa_id, concedido) VALUES (?, ?, ?, ?)",
                        u.getId(), ids.get(codigo), u.getEmpresaId(), concedido);
                guardados.put(codigo, concedido);
            }
        });
        permisosService.limpiar();
        auditoriaService.exito(AccionAuditoria.USUARIO_PERMISOS_CAMBIAR, EntidadAuditada.USUARIO, u.getId(),
                "antes: " + new TreeMap<>(antes) + " | después: " + guardados);
        return ver(usuarioId);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * @param id persona
     * @return la persona de la empresa con sus roles
     */
    private Usuario usuario(Long id) {
        return usuarioRepository.findWithRolesById(id).orElseThrow(() -> new NoEncontradoException("Usuario", id));
    }

    /**
     * @param u persona
     * @return permisos que le dan sus roles
     */
    private static Set<String> delRol(Usuario u) {
        Set<String> codigos = new HashSet<>();
        for (Rol r : u.getRoles()) {
            r.getPermisos().forEach(p -> codigos.add(p.getCodigo()));
        }
        return codigos;
    }

    /**
     * @param u persona
     * @return ajustes guardados de su empresa
     */
    private Map<String, Boolean> ajustes(Usuario u) {
        Map<String, Boolean> mapa = new HashMap<>();
        jdbc.query("SELECT p.codigo, up.concedido FROM usuario_permiso up JOIN permiso p ON p.id = up.permiso_id "
                + "WHERE up.usuario_id = ? AND up.empresa_id = ?",
                rs -> {
                    mapa.put(rs.getString(1), rs.getBoolean(2));
                }, u.getId(), u.getEmpresaId());
        return mapa;
    }
}
