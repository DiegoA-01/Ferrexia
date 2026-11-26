package com.proyecto.supply_core.plataforma.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.plataforma.dto.PlanDtos;
import com.proyecto.supply_core.plataforma.entity.Plan;
import com.proyecto.supply_core.plataforma.repository.ModuloRepository;
import com.proyecto.supply_core.plataforma.repository.PlanRepository;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;

/** Planes desde la spec 024 (D11): solo se consultan; crear y editar ya no existen. */
class PlanPlataformaServiceTest {

    private final PlanRepository planes = mock(PlanRepository.class);
    private final ModuloRepository modulos = mock(ModuloRepository.class);
    private final SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
    private final AccesoEmpresaService acceso = mock(AccesoEmpresaService.class);
    private final PlataformaAuditoriaService auditoria = mock(PlataformaAuditoriaService.class);
    private final PlanPlataformaService service = new PlanPlataformaService(planes, modulos, suscripciones, acceso,
            auditoria);

    @Test
    void listaLosPlanesGuardadosConCuantasEmpresasLosTienen() {
        Plan basico = new Plan();
        basico.setId(1L);
        basico.setCodigo("BASICO");
        basico.setNombre("Básico");
        basico.getModulos().addAll(Set.of("INVENTARIO", "COMPRAS"));
        when(planes.findAllByOrderByCodigoAsc()).thenReturn(List.of(basico));
        when(suscripciones.countByPlanId(1L)).thenReturn(4L);

        var lista = service.listar();

        assertEquals(List.of("COMPRAS", "INVENTARIO"), lista.get(0).modulos());
        assertEquals(4L, lista.get(0).empresas());
    }

    @Test
    void crearUnPlanYaNoSePuede() {
        var ex = assertThrows(NegocioException.class,
                () -> service.crear(new PlanDtos.Crear("PRO_PLUS", "Pro Plus", Set.of("COMPRAS"))));

        assertEquals(PlanPlataformaService.PLANES_RETIRADOS, ex.getCodigo());
        verify(planes, never()).save(any());
        verifyNoInteractions(acceso, auditoria);
    }

    @Test
    void editarUnPlanYaNoSePuede() {
        var ex = assertThrows(NegocioException.class,
                () -> service.editar(1L, new PlanDtos.Editar("Básico plus", Set.of("REPORTES"), false)));

        assertEquals(PlanPlataformaService.PLANES_RETIRADOS, ex.getCodigo());
        verify(planes, never()).save(any());
        verifyNoInteractions(acceso, auditoria);
    }
}
