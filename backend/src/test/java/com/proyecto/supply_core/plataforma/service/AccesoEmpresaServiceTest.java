package com.proyecto.supply_core.plataforma.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.plataforma.entity.EmpresaModulo;
import com.proyecto.supply_core.plataforma.entity.Modulo;
import com.proyecto.supply_core.plataforma.entity.Plan;
import com.proyecto.supply_core.plataforma.entity.Suscripcion;
import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;
import com.proyecto.supply_core.plataforma.repository.EmpresaModuloRepository;
import com.proyecto.supply_core.plataforma.repository.ModuloRepository;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;

class AccesoEmpresaServiceTest {

    private final SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
    private final EmpresaModuloRepository excepciones = mock(EmpresaModuloRepository.class);
    private final ModuloRepository catalogo = mock(ModuloRepository.class);
    private AccesoEmpresaService service;
    private Suscripcion suscripcion;

    @BeforeEach
    void setUp() {
        service = new AccesoEmpresaService(suscripciones, excepciones, catalogo, 0); // sin memoria: cada llamada lee
        List<Modulo> todos = List.of(modulo("COMPRAS"), modulo("INVENTARIO"), modulo("REPORTES"), modulo("VENTAS"));
        when(catalogo.findAllByOrderByCodigoAsc()).thenReturn(todos);
        Plan plan = new Plan();
        plan.setCodigo("BASICO");
        plan.getModulos().addAll(Set.of("INVENTARIO", "COMPRAS"));
        suscripcion = new Suscripcion();
        suscripcion.setEmpresaId(7L);
        suscripcion.setPlan(plan);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        when(suscripciones.findByEmpresaId(7L)).thenReturn(Optional.of(suscripcion));
        when(excepciones.findByEmpresaId(7L)).thenReturn(List.of());
    }

    /** Spec 024: el plan BASICO ya no limita; la empresa tiene todo el catálogo, incluido Ventas. */
    @Test
    void sinPlanesUnaEmpresaTieneTodosLosModulosDelCatalogo() {
        assertDoesNotThrow(() -> service.exigirSuscripcion(7L, "POST"));
        assertDoesNotThrow(() -> service.exigirModulo(7L, "COMPRAS"));
        assertDoesNotThrow(() -> service.exigirModulo(7L, "REPORTES"));
        assertDoesNotThrow(() -> service.exigirModulo(7L, "VENTAS"));
        assertEquals(Set.of("COMPRAS", "INVENTARIO", "REPORTES", "VENTAS"), service.resumen(7L).modulos());

        var error = assertThrows(AccesoDenegadoException.class, () -> service.exigirModulo(7L, "NO_EXISTE"));
        assertEquals("MODULO_NO_CONTRATADO", error.getCodigo());
    }

    @Test
    void enPruebaTieneElMismoAccesoQueActiva() {
        suscripcion.setEstado(EstadoSuscripcion.PRUEBA);

        assertDoesNotThrow(() -> service.exigirSuscripcion(7L, "PUT"));
    }

    @Test
    void unaEmpresaMorosaSoloConsulta() {
        suscripcion.setEstado(EstadoSuscripcion.MOROSA);

        assertDoesNotThrow(() -> service.exigirSuscripcion(7L, "GET"));
        assertDoesNotThrow(() -> service.exigirSuscripcion(7L, "head"));
        for (String metodo : new String[] { "POST", "PUT", "PATCH", "DELETE" }) {
            assertEquals("SUSCRIPCION_MOROSA",
                    assertThrows(AccesoDenegadoException.class, () -> service.exigirSuscripcion(7L, metodo)).getCodigo());
        }
    }

    @Test
    void suspendidaYCanceladaNoEntranNiParaConsultar() {
        suscripcion.setEstado(EstadoSuscripcion.SUSPENDIDA);
        assertEquals("SUSCRIPCION_SUSPENDIDA",
                assertThrows(AccesoDenegadoException.class, () -> service.exigirSuscripcion(7L, "GET")).getCodigo());
        assertEquals("SUSCRIPCION_SUSPENDIDA",
                assertThrows(AccesoDenegadoException.class, () -> service.exigirPuedeEntrar(7L)).getCodigo());

        suscripcion.setEstado(EstadoSuscripcion.CANCELADA);
        var cancelada = assertThrows(AccesoDenegadoException.class, () -> service.exigirPuedeEntrar(7L));
        assertEquals("SUSCRIPCION_SUSPENDIDA", cancelada.getCodigo());
        assertEquals(true, cancelada.getMessage().contains("cancelada"));
    }

    @Test
    void unaEmpresaSinSuscripcionNoEntra() {
        var error = assertThrows(AccesoDenegadoException.class, () -> service.exigirSuscripcion(99L, "GET"));

        assertEquals("SUSCRIPCION_SUSPENDIDA", error.getCodigo());
    }

    /** Solo cuenta el módulo apagado; una fila habilitada (de antes de V46) vale lo mismo que no tenerla. */
    @Test
    void elModuloApagadoNoSePuedeUsar() {
        when(excepciones.findByEmpresaId(7L)).thenReturn(List.of(excepcion("REPORTES", true), excepcion("COMPRAS", false)));

        assertDoesNotThrow(() -> service.exigirModulo(7L, "REPORTES"));
        assertDoesNotThrow(() -> service.exigirModulo(7L, "INVENTARIO"));
        assertEquals("MODULO_NO_CONTRATADO",
                assertThrows(AccesoDenegadoException.class, () -> service.exigirModulo(7L, "COMPRAS")).getCodigo());
        assertEquals(Set.of("INVENTARIO", "REPORTES", "VENTAS"), service.resumen(7L).modulos());
        assertEquals("BASICO", service.resumen(7L).plan());
    }

    @Test
    void conMemoriaNoVuelveALeerHastaQueSeInvalida() {
        AccesoEmpresaService conMemoria = new AccesoEmpresaService(suscripciones, excepciones, catalogo, 60);

        conMemoria.exigirModulo(7L, "COMPRAS");
        conMemoria.exigirModulo(7L, "COMPRAS");
        verify(suscripciones, times(1)).findByEmpresaId(7L);

        suscripcion.setEstado(EstadoSuscripcion.SUSPENDIDA);
        assertDoesNotThrow(() -> conMemoria.exigirModulo(7L, "COMPRAS")); // todavía recuerda lo anterior

        conMemoria.invalidar(7L);
        assertThrows(AccesoDenegadoException.class, () -> conMemoria.exigirPuedeEntrar(7L));
        verify(suscripciones, times(2)).findByEmpresaId(7L);

        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        conMemoria.invalidarTodo();
        assertDoesNotThrow(() -> conMemoria.exigirPuedeEntrar(7L));
    }

    private static Modulo modulo(String codigo) {
        Modulo m = mock(Modulo.class);
        when(m.getCodigo()).thenReturn(codigo);
        return m;
    }

    private static EmpresaModulo excepcion(String modulo, boolean habilitado) {
        EmpresaModulo e = new EmpresaModulo();
        e.setEmpresaId(7L);
        e.setModuloCodigo(modulo);
        e.setHabilitado(habilitado);
        return e;
    }
}
