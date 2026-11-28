package com.proyecto.supply_core.grupo.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.grupo.entity.TrasladoGrupo;

import jakarta.persistence.LockModeType;

/**
 * Acceso a {@code traslado_grupo}. La entidad no tiene {@code @TenantId}: TODA consulta filtra a mano por la empresa
 * como origen o destino.
 */
public interface TrasladoGrupoRepository extends Repository<TrasladoGrupo, Long> {

    /**
     * Guarda un traslado.
     *
     * @param traslado traslado nuevo o modificado
     * @param <S>      tipo del traslado
     * @return el traslado guardado
     */
    <S extends TrasladoGrupo> S save(S traslado);

    /**
     * Guarda un traslado y escribe en la base de inmediato.
     *
     * @param traslado traslado nuevo o modificado
     * @param <S>      tipo del traslado
     * @return el traslado guardado
     */
    <S extends TrasladoGrupo> S saveAndFlush(S traslado);

    /**
     * Traslados enviados o recibidos por la empresa, los más nuevos primero.
     *
     * @param empresaId empresa del contexto
     * @param pageable  página
     * @return página de traslados
     */
    @Query(value = "select t from TrasladoGrupo t where t.empresaOrigenId = :empresaId or t.empresaDestinoId = :empresaId "
            + "order by t.enviadoEn desc, t.id desc",
            countQuery = "select count(t) from TrasladoGrupo t where t.empresaOrigenId = :empresaId "
                    + "or t.empresaDestinoId = :empresaId")
    Page<TrasladoGrupo> deLaEmpresa(@Param("empresaId") Long empresaId, Pageable pageable);

    /**
     * Un traslado, solo si la empresa es su origen o su destino.
     *
     * @param id        traslado
     * @param empresaId empresa del contexto
     * @return traslado, si es de la empresa
     */
    @Query("select t from TrasladoGrupo t where t.id = :id "
            + "and (t.empresaOrigenId = :empresaId or t.empresaDestinoId = :empresaId)")
    Optional<TrasladoGrupo> deLaEmpresa(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /**
     * Igual que {@link #deLaEmpresa(Long, Long)} con bloqueo de escritura (recibir y anular).
     *
     * @param id        traslado
     * @param empresaId empresa del contexto
     * @return traslado bloqueado, si es de la empresa
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TrasladoGrupo t where t.id = :id "
            + "and (t.empresaOrigenId = :empresaId or t.empresaDestinoId = :empresaId)")
    Optional<TrasladoGrupo> deLaEmpresaBloqueado(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /**
     * Indica si la empresa tiene traslados en tránsito, como origen o como destino.
     *
     * @param empresaId empresa
     * @return {@code true} si hay alguno
     */
    @Query("select count(t) > 0 from TrasladoGrupo t "
            + "where t.estado = com.proyecto.supply_core.grupo.enums.EstadoTrasladoGrupo.EN_TRANSITO "
            + "and (t.empresaOrigenId = :empresaId or t.empresaDestinoId = :empresaId)")
    boolean hayEnTransito(@Param("empresaId") Long empresaId);

    /**
     * @param grupoId grupo
     * @return {@code true} si el grupo tiene traslados en su historial (en cualquier estado)
     */
    boolean existsByGrupoId(Long grupoId);
}
