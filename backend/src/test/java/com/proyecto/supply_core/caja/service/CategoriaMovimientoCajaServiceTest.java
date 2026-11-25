package com.proyecto.supply_core.caja.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.dto.CategoriaMovimientoCajaCambio;
import com.proyecto.supply_core.caja.dto.CategoriaMovimientoCajaRequest;
import com.proyecto.supply_core.caja.entity.CategoriaMovimientoCaja;
import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;
import com.proyecto.supply_core.caja.repository.CategoriaMovimientoCajaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;

/** Categorías de ingresos y egresos de caja (spec 021). */
@ExtendWith(MockitoExtension.class)
class CategoriaMovimientoCajaServiceTest {

    @Mock private CategoriaMovimientoCajaRepository repository;
    @Mock private AuditoriaService auditoriaService;

    private CategoriaMovimientoCajaService servicio;

    /** Arma el servicio. */
    @BeforeEach
    void crear() {
        servicio = new CategoriaMovimientoCajaService(repository, auditoriaService);
    }

    /**
     * @param tipo   tipo
     * @param activa estado
     * @return categoría 4 llamada Papelería
     */
    private static CategoriaMovimientoCaja categoria(TipoCategoriaMovimiento tipo, boolean activa) {
        CategoriaMovimientoCaja c = new CategoriaMovimientoCaja();
        c.setId(4L);
        c.setTipo(tipo);
        c.setNombre("Papelería");
        c.setActiva(activa);
        return c;
    }

    /** Crear guarda el nombre sin espacios de más. */
    @Test
    void creaConElNombreLimpio() {
        when(repository.existsByTipoAndNombreIgnoreCaseAndIdNot(TipoCategoriaMovimiento.EGRESO, "Pago domiciliario", -1L))
                .thenReturn(false);

        var r = servicio.crear(new CategoriaMovimientoCajaRequest(TipoCategoriaMovimiento.EGRESO, "  Pago   domiciliario "));

        assertThat(r.nombre()).isEqualTo("Pago domiciliario");
        assertThat(r.activa()).isTrue();
        verify(repository).save(any());
    }

    /** Un nombre repetido en el mismo tipo responde 409 y no guarda nada. */
    @Test
    void elNombreRepetidoEnElMismoTipoSeRechaza() {
        when(repository.existsByTipoAndNombreIgnoreCaseAndIdNot(eq(TipoCategoriaMovimiento.EGRESO), anyString(), anyLong()))
                .thenReturn(true);

        NegocioException e = catchThrowableOfType(NegocioException.class,
                () -> servicio.crear(new CategoriaMovimientoCajaRequest(TipoCategoriaMovimiento.EGRESO, "papelería")));

        assertThat(e).isNotNull();
        assertThat(e.getCodigo()).isEqualTo("CATEGORIA_DUPLICADA");
        verify(repository, never()).save(any());
    }

    /** Desactivar una categoría la oculta: no se borra (los movimientos ya registrados la siguen nombrando). */
    @Test
    void desactivarNoBorra() {
        CategoriaMovimientoCaja c = categoria(TipoCategoriaMovimiento.EGRESO, true);
        when(repository.findById(4L)).thenReturn(Optional.of(c));

        var r = servicio.editar(4L, new CategoriaMovimientoCajaCambio("Papelería", false));

        assertThat(r.activa()).isFalse();
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }

    /** Para un movimiento sirve solo una categoría activa del tipo pedido. */
    @Test
    void paraUnMovimientoSoloSirveUnaActivaDelTipo() {
        when(repository.findById(4L)).thenReturn(Optional.of(categoria(TipoCategoriaMovimiento.EGRESO, true)));
        assertThat(servicio.paraMovimiento(4L, TipoCategoriaMovimiento.EGRESO).getId()).isEqualTo(4L);

        NegocioException otroTipo = catchThrowableOfType(NegocioException.class,
                () -> servicio.paraMovimiento(4L, TipoCategoriaMovimiento.INGRESO));
        assertThat(otroTipo.getCodigo()).isEqualTo("CATEGORIA_INVALIDA");

        when(repository.findById(4L)).thenReturn(Optional.of(categoria(TipoCategoriaMovimiento.EGRESO, false)));
        NegocioException inactiva = catchThrowableOfType(NegocioException.class,
                () -> servicio.paraMovimiento(4L, TipoCategoriaMovimiento.EGRESO));
        assertThat(inactiva.getCodigo()).isEqualTo("CATEGORIA_INVALIDA");

        when(repository.findById(8L)).thenReturn(Optional.empty());
        NegocioException ajena = catchThrowableOfType(NegocioException.class,
                () -> servicio.paraMovimiento(8L, TipoCategoriaMovimiento.EGRESO));
        assertThat(ajena.getCodigo()).isEqualTo("CATEGORIA_INVALIDA");
    }

    /** La empresa nueva recibe las 6 categorías por defecto una sola vez. */
    @Test
    void siembraLasDeDefectoSoloSiNoHayNinguna() {
        when(repository.hayAlguna()).thenReturn(false);
        servicio.sembrarPorDefecto();
        verify(repository, times(6)).save(any());

        when(repository.hayAlguna()).thenReturn(true);
        servicio.sembrarPorDefecto();
        verify(repository, times(6)).save(any());
    }
}
