package com.proyecto.supply_core.usuario.service;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.security.service.PermisosService;
import com.proyecto.supply_core.usuario.dto.PermisoResponse;
import com.proyecto.supply_core.usuario.dto.RolRequest;
import com.proyecto.supply_core.usuario.dto.RolResponse;
import com.proyecto.supply_core.usuario.entity.Permiso;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.repository.PermisoRepository;
import com.proyecto.supply_core.usuario.repository.RolRepository;

/**
 * Roles de la empresa (spec 022): las plantillas del sistema (solo lectura) y los roles propios, que la empresa crea a
 * partir de una plantilla o desde cero eligiendo permisos. Un rol propio solo existe para su empresa; su código es único
 * entre todas las empresas porque viaja en el JWT. No se borra un rol que alguien tiene.
 */
@Service
public class RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final PermisosService permisosService;
    private final AuditoriaService auditoriaService;
    private final JdbcTemplate jdbc;

    /**
     * @param rolRepository     roles
     * @param permisoRepository catálogo de permisos
     * @param permisosService   se limpia al cambiar permisos de un rol
     * @param auditoriaService  registro de cada cambio
     * @param jdbc              cuántas personas tienen un rol (de la empresa)
     */
    public RolService(RolRepository rolRepository, PermisoRepository permisoRepository, PermisosService permisosService,
            AuditoriaService auditoriaService, JdbcTemplate jdbc) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.permisosService = permisosService;
        this.auditoriaService = auditoriaService;
        this.jdbc = jdbc;
    }

    /**
     * @return plantillas y roles propios de la empresa, con sus permisos
     */
    @Transactional(readOnly = true)
    public List<RolResponse> listar() {
        return rolRepository.visibles(EmpresaContexto.requerida()).stream().map(RolResponse::of).toList();
    }

    /**
     * @return catálogo de permisos por módulo
     */
    @Transactional(readOnly = true)
    public List<PermisoResponse> permisos() {
        return permisoRepository.findAllByOrderByModuloAscNombreAsc().stream().map(PermisoResponse::of).toList();
    }

    /**
     * @param req nombre, descripción y permisos
     * @return rol propio creado
     * @throws NegocioException si el nombre ya lo usa otro rol visible o algún permiso no sirve
     */
    @Transactional
    public RolResponse crear(RolRequest req) {
        Long empresa = EmpresaContexto.requerida();
        String nombre = limpiar(req.nombre());
        validarNombre(nombre, -1L, empresa);
        Rol r = new Rol();
        r.setEmpresaId(empresa);
        r.setNombre(nombre);
        r.setDescripcion(req.descripcion() == null || req.descripcion().isBlank() ? null : req.descripcion().trim());
        r.setCodigo(codigoNuevo(empresa, nombre));
        r.setPermisos(permisos(req.permisos()));
        rolRepository.save(r);
        permisosService.limpiar();
        auditoriaService.exito(AccionAuditoria.ROL_CREAR, EntidadAuditada.ROL, r.getId(),
                "codigo=" + r.getCodigo() + "; permisos=" + codigos(r));
        return RolResponse.of(r);
    }

    /**
     * @param id  rol propio
     * @param req nombre, descripción y permisos nuevos
     * @return rol actualizado
     * @throws NoEncontradoException si no es de la empresa
     * @throws NegocioException      si es una plantilla, el nombre ya existe o algún permiso no sirve
     */
    @Transactional
    public RolResponse editar(Long id, RolRequest req) {
        Long empresa = EmpresaContexto.requerida();
        Rol r = propio(id, empresa);
        String nombre = limpiar(req.nombre());
        validarNombre(nombre, id, empresa);
        String antes = r.getNombre() + " " + codigos(r);
        r.setNombre(nombre);
        r.setDescripcion(req.descripcion() == null || req.descripcion().isBlank() ? null : req.descripcion().trim());
        r.getPermisos().clear();
        r.getPermisos().addAll(permisos(req.permisos()));
        rolRepository.flush();
        permisosService.limpiar();
        auditoriaService.exito(AccionAuditoria.ROL_EDITAR, EntidadAuditada.ROL, id,
                "antes: " + antes + " | después: " + nombre + " " + codigos(r));
        return RolResponse.of(r);
    }

    /**
     * @param id rol propio sin personas
     * @throws NoEncontradoException si no es de la empresa
     * @throws NegocioException      si es una plantilla o alguien lo tiene o lo usa una política
     */
    @Transactional
    public void eliminar(Long id) {
        Long empresa = EmpresaContexto.requerida();
        Rol r = propio(id, empresa);
        Integer usuarios = jdbc.queryForObject("SELECT COUNT(*) FROM usuario_rol ur JOIN usuario u ON u.id = ur.usuario_id "
                + "WHERE ur.rol_id = ? AND u.empresa_id = ?", Integer.class, id, empresa);
        if (usuarios != null && usuarios > 0) {
            throw new NegocioException("ROL_EN_USO", "El rol " + r.getNombre() + " lo tienen " + usuarios
                    + " persona(s): quíteselo antes de borrarlo");
        }
        String codigo = r.getCodigo();
        try {
            r.getPermisos().clear();
            rolRepository.delete(r);
            rolRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new NegocioException("ROL_EN_USO", "El rol " + r.getNombre() + " lo usa una política de aprobación");
        }
        permisosService.limpiar();
        auditoriaService.exito(AccionAuditoria.ROL_ELIMINAR, EntidadAuditada.ROL, id, "codigo=" + codigo);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * @param id      rol
     * @param empresa empresa
     * @return el rol propio de la empresa
     */
    private Rol propio(Long id, Long empresa) {
        Rol r = rolRepository.visiblePorId(id, empresa).orElseThrow(() -> new NoEncontradoException("Rol", id));
        if (r.getEmpresaId() == null) {
            throw new NegocioException("PLANTILLA_SOLO_LECTURA",
                    "Las plantillas no se cambian: cópiela como un rol propio y cambie la copia");
        }
        return r;
    }

    /**
     * @param codigos permisos pedidos
     * @return entidades
     * @throws NegocioException si alguno no existe o es del portal del proveedor
     */
    private Set<Permiso> permisos(Set<String> codigos) {
        Set<String> limpios = codigos.stream().map(c -> c.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        if (limpios.contains(Permisos.PORTAL_PROVEEDOR)) {
            throw new NegocioException("PERMISO_NO_PERMITIDO", "El portal del proveedor no se puede dar en un rol propio");
        }
        Set<Permiso> encontrados = new HashSet<>(permisoRepository.findByCodigoIn(limpios));
        if (encontrados.size() != limpios.size()) {
            Set<String> faltan = new TreeSet<>(limpios);
            encontrados.forEach(p -> faltan.remove(p.getCodigo()));
            throw new NegocioException("PERMISO_INEXISTENTE", "No existen los permisos " + faltan);
        }
        return encontrados;
    }

    /**
     * @param nombre  nombre limpio
     * @param id      rol que se edita (o -1)
     * @param empresa empresa
     */
    private void validarNombre(String nombre, Long id, Long empresa) {
        boolean repetido = rolRepository.visibles(empresa).stream()
                .anyMatch(r -> !r.getId().equals(id) && r.getNombre().equalsIgnoreCase(nombre));
        if (repetido) {
            throw new NegocioException("ROL_DUPLICADO", "Ya existe un rol llamado " + nombre);
        }
    }

    /**
     * @param empresa empresa
     * @param nombre  nombre del rol
     * @return código único: {@code E<empresa>_<NOMBRE>} (máximo 50 caracteres)
     */
    private String codigoNuevo(Long empresa, String nombre) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        String prefijo = "E" + empresa + "_";
        base = prefijo + (base.isEmpty() ? "ROL" : base);
        base = base.length() > 46 ? base.substring(0, 46) : base;
        String codigo = base;
        for (int i = 2; rolRepository.existsByCodigo(codigo); i++) {
            codigo = base + "_" + i;
        }
        return codigo;
    }

    /**
     * @param nombre nombre escrito
     * @return sin espacios de más
     */
    private static String limpiar(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ");
    }

    /**
     * @param r rol
     * @return sus permisos ordenados
     */
    private static Set<String> codigos(Rol r) {
        return r.getPermisos().stream().map(Permiso::getCodigo).collect(Collectors.toCollection(TreeSet::new));
    }
}
