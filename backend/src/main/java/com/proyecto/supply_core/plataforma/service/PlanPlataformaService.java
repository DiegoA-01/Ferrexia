package com.proyecto.supply_core.plataforma.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.plataforma.dto.PlanDtos;
import com.proyecto.supply_core.plataforma.repository.ModuloRepository;
import com.proyecto.supply_core.plataforma.repository.PlanRepository;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;

/**
 * Catálogo de módulos y planes de la plataforma. Desde la spec 024 (decisión D11) los planes se quitaron: los módulos
 * se habilitan por empresa al crearla y desde su detalle. Los planes quedan guardados solo para consulta (por si algún
 * día se cobra por plan) y ya no se crean ni se editan.
 */
@Service
public class PlanPlataformaService {

    /** Código del error al intentar crear o editar un plan. */
    public static final String PLANES_RETIRADOS = "PLANES_RETIRADOS";

    private final PlanRepository planRepository;
    private final ModuloRepository moduloRepository;
    private final SuscripcionRepository suscripcionRepository;

    /**
     * @param planRepository        planes
     * @param moduloRepository      catálogo de módulos
     * @param suscripcionRepository para saber cuántas empresas tienen guardado cada plan
     * @param accesoEmpresa         no se usa desde la spec 024 (se conserva la firma)
     * @param auditoria             no se usa desde la spec 024 (se conserva la firma)
     */
    public PlanPlataformaService(PlanRepository planRepository, ModuloRepository moduloRepository,
            SuscripcionRepository suscripcionRepository, AccesoEmpresaService accesoEmpresa,
            PlataformaAuditoriaService auditoria) {
        this.planRepository = planRepository;
        this.moduloRepository = moduloRepository;
        this.suscripcionRepository = suscripcionRepository;
    }

    /**
     * Catálogo de módulos (el panel del alta de empresas lo usa).
     *
     * @return módulos por código
     */
    @Transactional(readOnly = true)
    public List<PlanDtos.ModuloRespuesta> modulos() {
        return moduloRepository.findAllByOrderByCodigoAsc().stream().map(PlanDtos.ModuloRespuesta::of).toList();
    }

    /**
     * Planes guardados, solo para consulta.
     *
     * @return planes por código
     */
    @Transactional(readOnly = true)
    public List<PlanDtos.Respuesta> listar() {
        return planRepository.findAllByOrderByCodigoAsc().stream()
                .map(p -> PlanDtos.Respuesta.of(p, suscripcionRepository.countByPlanId(p.getId()))).toList();
    }

    /**
     * Ya no se crean planes (spec 024).
     *
     * @param req no se usa
     * @return nunca devuelve
     * @throws NegocioException siempre, con el código {@value #PLANES_RETIRADOS}
     */
    public PlanDtos.Respuesta crear(PlanDtos.Crear req) {
        throw retirados();
    }

    /**
     * Ya no se editan planes (spec 024).
     *
     * @param id  no se usa
     * @param req no se usa
     * @return nunca devuelve
     * @throws NegocioException siempre, con el código {@value #PLANES_RETIRADOS}
     */
    public PlanDtos.Respuesta editar(Long id, PlanDtos.Editar req) {
        throw retirados();
    }

    /** @return el error de planes retirados */
    private static NegocioException retirados() {
        return new NegocioException(PLANES_RETIRADOS,
                "Los planes ya no existen: los módulos se habilitan en cada empresa");
    }
}
