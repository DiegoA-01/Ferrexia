package com.proyecto.supply_core.usuario.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.usuario.entity.Rol;

/**
 * Acceso a la tabla {@code rol}. Hay plantillas del sistema ({@code empresa_id} NULL, para todas las empresas) y roles
 * propios de cada empresa (spec 022). La tabla no lleva {@code @TenantId} por las plantillas, así que toda búsqueda pasa
 * por los métodos de aquí, que solo devuelven las plantillas y los roles de la empresa de la sesión.
 */
public interface RolRepository extends JpaRepository<Rol, Long> {

    /**
     * @param codigo  código exacto
     * @param empresa empresa ({@code null} = solo plantillas)
     * @return rol visible para la empresa
     */
    @Query("select r from Rol r where r.codigo = :codigo and (r.empresaId is null or r.empresaId = :empresa)")
    Optional<Rol> visiblePorCodigo(@Param("codigo") String codigo, @Param("empresa") Long empresa);

    /**
     * @param codigos códigos
     * @param empresa empresa ({@code null} = solo plantillas)
     * @return roles visibles para la empresa (los que no existen o son de otra empresa no aparecen)
     */
    @Query("select r from Rol r where r.codigo in :codigos and (r.empresaId is null or r.empresaId = :empresa)")
    List<Rol> visiblesPorCodigo(@Param("codigos") Collection<String> codigos, @Param("empresa") Long empresa);

    /**
     * @param empresa empresa ({@code null} = solo plantillas)
     * @return plantillas y roles propios de la empresa, por nombre
     */
    @Query("select r from Rol r where r.empresaId is null or r.empresaId = :empresa order by r.nombre")
    List<Rol> visibles(@Param("empresa") Long empresa);

    /**
     * @param id      rol
     * @param empresa empresa
     * @return rol visible para la empresa
     */
    @Query("select r from Rol r where r.id = :id and (r.empresaId is null or r.empresaId = :empresa)")
    Optional<Rol> visiblePorId(@Param("id") Long id, @Param("empresa") Long empresa);

    /**
     * @param codigo código
     * @return {@code true} si algún rol (de cualquier empresa) ya usa el código
     */
    boolean existsByCodigo(String codigo);

    /**
     * @param codigo código exacto
     * @return el rol visible para la empresa de la sesión (sin sesión, solo plantillas)
     */
    default Optional<Rol> findByCodigo(String codigo) {
        return visiblePorCodigo(codigo, EmpresaContexto.opcional());
    }

    /**
     * @param codigos códigos
     * @return roles visibles para la empresa de la sesión (sin sesión, solo plantillas)
     */
    default List<Rol> findByCodigoIn(Collection<String> codigos) {
        return visiblesPorCodigo(codigos, EmpresaContexto.opcional());
    }

    /**
     * @return plantillas y roles propios de la empresa de la sesión, por nombre
     */
    default List<Rol> findAllByOrderByNombreAsc() {
        return visibles(EmpresaContexto.opcional());
    }
}
