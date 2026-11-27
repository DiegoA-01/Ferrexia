package com.proyecto.supply_core.grupo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.EnviarTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.LineaEnvioRequest;
import com.proyecto.supply_core.grupo.entity.TrasladoGrupo;
import com.proyecto.supply_core.grupo.enums.EstadoTrasladoGrupo;
import com.proyecto.supply_core.grupo.repository.TrasladoGrupoRepository;
import com.proyecto.supply_core.grupo.service.TrasladoGrupoPasos.Referencia;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/** Pasos del envío de un traslado: destino del grupo, emparejar por código y salida al costo vigente (spec 035). */
class TrasladoGrupoPasosTest {

    private final EmpresaRepository empresas = mock(EmpresaRepository.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final BodegaRepository bodegas = mock(BodegaRepository.class);
    private final TrasladoGrupoRepository traslados = mock(TrasladoGrupoRepository.class);
    private final MovimientoInventarioService movimientos = mock(MovimientoInventarioService.class);
    private final ConsecutivoGenerator consecutivos = mock(ConsecutivoGenerator.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final TrasladoGrupoPasos pasos = new TrasladoGrupoPasos(empresas, productos, bodegas, traslados, movimientos,
            consecutivos, auditoria);

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(7L, "u@x.com", "Persona", Set.of("ALMACENISTA"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        EmpresaContexto.fijar(10L);
        when(empresas.findById(10L)).thenReturn(Optional.of(empresa(10L, 2L)));
        when(empresas.findById(11L)).thenReturn(Optional.of(empresa(11L, 2L)));
        when(empresas.findById(12L)).thenReturn(Optional.of(empresa(12L, 3L)));
        when(empresas.findById(13L)).thenReturn(Optional.of(empresa(13L, null)));
    }

    @AfterEach
    void limpiar() {
        EmpresaContexto.limpiar();
        RequestContextHolder.resetRequestAttributes();
    }

    private static Empresa empresa(Long id, Long grupoId) {
        Empresa e = new Empresa();
        e.setId(id);
        e.setRazonSocial("Empresa " + id);
        e.setGrupoId(grupoId);
        return e;
    }

    private static Producto producto(Long id, String codigo, String barras) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo(codigo);
        p.setNombre("Producto " + codigo);
        p.setCodigoBarras(barras);
        p.setActivo(true);
        return p;
    }

    @Test
    void elDestinoDebeSerOtraEmpresaDelMismoGrupo() {
        assertEquals(11L, pasos.exigirDestinoDelGrupo(11L).getId());
        assertThrows(NoEncontradoException.class, () -> pasos.exigirDestinoDelGrupo(12L)); // otro grupo
        assertThrows(NoEncontradoException.class, () -> pasos.exigirDestinoDelGrupo(13L)); // sin grupo
        assertThrows(NoEncontradoException.class, () -> pasos.exigirDestinoDelGrupo(10L)); // la misma
        assertThrows(NoEncontradoException.class, () -> pasos.exigirDestinoDelGrupo(99L)); // no existe
    }

    @Test
    void sinGrupoLaEmpresaActualNoEnviaANadie() {
        when(empresas.findById(10L)).thenReturn(Optional.of(empresa(10L, null)));

        assertThrows(NoEncontradoException.class, () -> pasos.exigirDestinoDelGrupo(13L));
    }

    @Test
    void emparejaPorCodigoYSiNoPorCodigoDeBarras() {
        Producto porCodigo = producto(51L, "TOR-1", null);
        Producto porBarras = producto(52L, "OTRO-CODIGO", "7701234");
        when(productos.findByCodigoIgnoreCase("TOR-1")).thenReturn(Optional.of(porCodigo));
        when(productos.findByCodigoIgnoreCase("CLA-2")).thenReturn(Optional.empty());
        when(productos.findFirstByCodigoBarras("7701234")).thenReturn(Optional.of(porBarras));
        when(productos.findByCodigoIgnoreCase("NADA")).thenReturn(Optional.empty());

        List<Long> parejas = pasos.emparejar(List.of(
                new Referencia(41L, "TOR-1", null, BigDecimal.ONE, BigDecimal.ONE),
                new Referencia(42L, "CLA-2", "7701234", BigDecimal.ONE, BigDecimal.ONE),
                new Referencia(43L, "NADA", null, BigDecimal.ONE, BigDecimal.ONE)));

        assertEquals(51L, parejas.get(0));
        assertEquals(52L, parejas.get(1));
        assertNull(parejas.get(2));
    }

    @Test
    void unaParejaInactivaAgrupadoraOServicioNoSirve() {
        Producto inactivo = producto(51L, "TOR-1", null);
        inactivo.setActivo(false);
        Producto agrupador = producto(52L, "TOR-2", null);
        agrupador.setEsAgrupador(true);
        Producto servicio = producto(53L, "TOR-3", null);
        servicio.setEsServicio(true);
        when(productos.findByCodigoIgnoreCase("TOR-1")).thenReturn(Optional.of(inactivo));
        when(productos.findByCodigoIgnoreCase("TOR-2")).thenReturn(Optional.of(agrupador));
        when(productos.findByCodigoIgnoreCase("TOR-3")).thenReturn(Optional.of(servicio));

        List<Long> parejas = pasos.emparejar(List.of(
                new Referencia(41L, "TOR-1", null, BigDecimal.ONE, BigDecimal.ONE),
                new Referencia(42L, "TOR-2", null, BigDecimal.ONE, BigDecimal.ONE),
                new Referencia(43L, "TOR-3", null, BigDecimal.ONE, BigDecimal.ONE)));

        assertNull(parejas.get(0));
        assertNull(parejas.get(1));
        assertNull(parejas.get(2));
    }

    @Test
    void laOrigenNoEnviaAgrupadoresNiServiciosNiInactivos() {
        Bodega b = new Bodega();
        b.setId(5L);
        b.setActivo(true);
        when(bodegas.findById(5L)).thenReturn(Optional.of(b));
        Producto agrupador = producto(41L, "GEN-1", null);
        agrupador.setEsAgrupador(true);
        Producto servicio = producto(42L, "SRV-1", null);
        servicio.setEsServicio(true);
        Producto inactivo = producto(43L, "OLD-1", null);
        inactivo.setActivo(false);
        when(productos.findById(41L)).thenReturn(Optional.of(agrupador));
        when(productos.findById(42L)).thenReturn(Optional.of(servicio));
        when(productos.findById(43L)).thenReturn(Optional.of(inactivo));

        assertEquals("PRODUCTO_AGRUPADOR", assertThrows(NegocioException.class, () -> pasos.referenciasOrigen(5L,
                List.of(new LineaEnvioRequest(41L, BigDecimal.ONE)))).getCodigo());
        assertEquals("PRODUCTO_SERVICIO", assertThrows(NegocioException.class, () -> pasos.referenciasOrigen(5L,
                List.of(new LineaEnvioRequest(42L, BigDecimal.ONE)))).getCodigo());
        assertEquals("PRODUCTO_INACTIVO", assertThrows(NegocioException.class, () -> pasos.referenciasOrigen(5L,
                List.of(new LineaEnvioRequest(43L, BigDecimal.ONE)))).getCodigo());
    }

    @Test
    void lasLineasDelMismoProductoSeSumanYElCostoEsElVigente() {
        Bodega b = new Bodega();
        b.setId(5L);
        b.setActivo(true);
        when(bodegas.findById(5L)).thenReturn(Optional.of(b));
        Producto p = producto(41L, "TOR-1", "  ");
        p.setCostoPromedio(new BigDecimal("7.5"));
        when(productos.findById(41L)).thenReturn(Optional.of(p));

        List<Referencia> refs = pasos.referenciasOrigen(5L, List.of(new LineaEnvioRequest(41L, new BigDecimal("2")),
                new LineaEnvioRequest(41L, new BigDecimal("3"))));

        assertEquals(1, refs.size());
        assertEquals(0, new BigDecimal("5").compareTo(refs.get(0).cantidad()));
        assertEquals(0, new BigDecimal("7.5").compareTo(refs.get(0).costo()));
        assertNull(refs.get(0).codigoBarras());
    }

    /** Si la destino salió del grupo entre la verificación y el envío, no se crea nada. */
    @Test
    void elEnvioSeRechazaSiLaDestinoYaNoEsDelMismoGrupo() {
        when(empresas.findById(11L)).thenReturn(Optional.of(empresa(11L, 3L)));
        List<Referencia> refs = List.of(new Referencia(41L, "TOR-1", null, new BigDecimal("5"), new BigDecimal("7.5")));

        assertThrows(com.proyecto.supply_core.common.exception.NoEncontradoException.class,
                () -> pasos.registrarEnvio(new EnviarTrasladoRequest(11L, 5L, List.of(), null), empresa(11L, 2L), refs,
                        List.of(51L)));
        org.mockito.Mockito.verify(traslados, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void elEnvioCreaElTrasladoYSacaElStockAlCostoDeCadaLinea() {
        when(consecutivos.siguiente("TG")).thenReturn("TG-000007");
        when(traslados.saveAndFlush(any())).thenAnswer(i -> {
            TrasladoGrupo t = i.getArgument(0);
            t.setId(30L);
            return t;
        });
        List<Referencia> refs = List.of(new Referencia(41L, "TOR-1", null, new BigDecimal("5"), new BigDecimal("7.5")),
                new Referencia(42L, "TOR-2", null, new BigDecimal("1"), new BigDecimal("2")));

        TrasladoGrupo t = pasos.registrarEnvio(new EnviarTrasladoRequest(11L, 5L, List.of(), "  nota  "),
                empresa(11L, 2L), refs, List.of(51L, 52L));

        assertEquals("TG-000007", t.getConsecutivo());
        assertEquals(EstadoTrasladoGrupo.EN_TRANSITO, t.getEstado());
        assertEquals(10L, t.getEmpresaOrigenId());
        assertEquals(11L, t.getEmpresaDestinoId());
        assertEquals(2L, t.getGrupoId());
        assertEquals("nota", t.getNota());
        assertEquals(51L, t.getLineas().get(0).getProductoDestinoId());
        InOrder orden = inOrder(traslados, movimientos);
        orden.verify(traslados).saveAndFlush(any());
        orden.verify(movimientos).registrarSalidaTrasladoGrupo(41L, 5L, new BigDecimal("5"), new BigDecimal("7.5"), 30L);
        orden.verify(movimientos).registrarSalidaTrasladoGrupo(42L, 5L, new BigDecimal("1"), new BigDecimal("2"), 30L);
        org.mockito.Mockito.verify(auditoria).exito(eq(com.proyecto.supply_core.auditoria.enums.AccionAuditoria.TRASLADO_GRUPO_ENVIAR),
                any(), eq(30L), any());
        org.mockito.Mockito.verify(movimientos, org.mockito.Mockito.never()).registrarEntradaTrasladoGrupo(anyLong(), anyLong(),
                any(), any(), any(), anyLong());
    }
}
