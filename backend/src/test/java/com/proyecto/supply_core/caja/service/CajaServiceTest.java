package com.proyecto.supply_core.caja.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.dto.CajaResponse;
import com.proyecto.supply_core.caja.entity.Caja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.empresa.repository.SedeRepository;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/** Listado de cajas con su ocupación (spec 015). */
@ExtendWith(MockitoExtension.class)
class CajaServiceTest {

    @Mock private CajaRepository cajaRepository;
    @Mock private TurnoCajaRepository turnoRepository;
    @Mock private SedeRepository sedeRepository;
    @Mock private BodegaRepository bodegaRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private UsuarioRepository usuarioRepository;

    private CajaService servicio;

    /** Arma el servicio con sus dependencias simuladas. */
    @BeforeEach
    void crear() {
        servicio = new CajaService(cajaRepository, turnoRepository, sedeRepository, bodegaRepository,
                empresaRepository, auditoriaService, usuarioRepository);
    }

    /**
     * @param id     caja
     * @param nombre nombre de la caja
     * @return caja activa en la sede y la bodega de prueba
     */
    private static Caja caja(long id, String nombre) {
        Sede sede = mock(Sede.class);
        when(sede.getId()).thenReturn(1L);
        when(sede.getNombre()).thenReturn("Sede principal");
        Bodega bodega = mock(Bodega.class);
        when(bodega.getId()).thenReturn(1L);
        when(bodega.getNombre()).thenReturn("Bodega principal");
        Caja c = new Caja();
        c.setId(id);
        c.setNombre(nombre);
        c.setSede(sede);
        c.setBodega(bodega);
        c.setPrefijoConsecutivo("C" + id);
        c.setTopeEfectivo(new BigDecimal("500000.00"));
        c.setActiva(true);
        return c;
    }

    /** Una caja con turno abierto trae quién lo tiene y desde cuándo; una libre no trae nada de eso. */
    @Test
    void listarMarcaLasCajasOcupadasConQuienYDesdeCuando() {
        Caja ocupada = caja(1, "Caja 1 mostrador");
        Caja libre = caja(2, "Caja 2 mostrador");
        LocalDateTime apertura = LocalDateTime.of(2026, 11, 20, 11, 1, 23);
        TurnoCaja turno = mock(TurnoCaja.class);
        when(turno.getCaja()).thenReturn(ocupada);
        when(turno.getCajeroUsuarioId()).thenReturn(10L);
        when(turno.getAbiertoEn()).thenReturn(apertura);
        Usuario cajero = mock(Usuario.class);
        when(cajero.getId()).thenReturn(10L);
        when(cajero.getNombreCompleto()).thenReturn("Cajero");
        when(turnoRepository.conCajaPorEstado(EstadoTurno.ABIERTO)).thenReturn(List.of(turno));
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(cajero));
        when(cajaRepository.listar(true)).thenReturn(List.of(ocupada, libre));

        List<CajaResponse> cajas = servicio.listar(true);

        assertThat(cajas).extracting(CajaResponse::nombre, CajaResponse::turnoAbierto, CajaResponse::turnoAbiertoPor,
                CajaResponse::turnoAbiertoDesde).containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Caja 1 mostrador", true, "Cajero", apertura),
                        org.assertj.core.groups.Tuple.tuple("Caja 2 mostrador", false, null, null));
    }

    /** La respuesta de una caja no tiene campos de dinero del turno: ni base, ni esperado, ni contado. */
    @Test
    void laRespuestaNoTraeMontosDelTurno() {
        assertThat(CajaResponse.class.getRecordComponents()).extracting(c -> c.getName())
                .doesNotContain("baseApertura", "esperadoEfectivo", "contadoEfectivo", "diferencia")
                .contains("turnoAbiertoPor", "turnoAbiertoDesde");
    }
}
