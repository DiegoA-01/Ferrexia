package com.proyecto.supply_core.grupo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.AnularTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.EnviarTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.LineaEnvioRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.LineaRecepcionTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.RecibirTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.TrasladoDetalleResponse;
import com.proyecto.supply_core.grupo.entity.TrasladoGrupo;
import com.proyecto.supply_core.grupo.entity.TrasladoGrupoLinea;
import com.proyecto.supply_core.grupo.enums.EstadoTrasladoGrupo;
import com.proyecto.supply_core.grupo.repository.TrasladoGrupoRepository;
import com.proyecto.supply_core.grupo.service.TrasladoGrupoPasos.Referencia;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.service.CostoPromedioService;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/** Reglas de los traslados entre empresas del grupo: enviar, recibir con faltante, anular y consultar (spec 035). */
class TrasladoGrupoServiceTest {

    private static final Long A = 10L;
    private static final Long B = 11L;

    private final EmpresaRepository empresas = mock(EmpresaRepository.class);
    private final TrasladoGrupoRepository traslados = mock(TrasladoGrupoRepository.class);
    private final TrasladoGrupoPasos pasos = mock(TrasladoGrupoPasos.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final BodegaRepository bodegas = mock(BodegaRepository.class);
    private final MovimientoInventarioService movimientos = mock(MovimientoInventarioService.class);
    private final CostoPromedioService costos = mock(CostoPromedioService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final TrasladoGrupoService service = new TrasladoGrupoService(empresas, traslados, pasos, productos, bodegas,
            movimientos, costos, auditoria, notificaciones);

    private Producto productoB;

    @BeforeEach
    void setUp() {
        sesion("ALMACENISTA");
        Empresa a = empresa(A, 2L);
        Empresa b = empresa(B, 2L);
        when(empresas.findById(A)).thenReturn(Optional.of(a));
        when(empresas.findById(B)).thenReturn(Optional.of(b));
        when(empresas.findAllById(any())).thenReturn(List.of(a, b));
        productoB = producto(51L, "TOR-1");
        when(productos.findById(51L)).thenReturn(Optional.of(productoB));
        when(productos.findAllById(any())).thenReturn(List.of(productoB));
        Bodega bodega = new Bodega();
        bodega.setId(8L);
        bodega.setNombre("Principal");
        bodega.setActivo(true);
        when(bodegas.findById(8L)).thenReturn(Optional.of(bodega));
        when(traslados.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        EmpresaContexto.limpiar();
        RequestContextHolder.resetRequestAttributes();
    }

    private static void sesion(String rol) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(7L, "u@x.com", "Persona", Set.of(rol), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static Empresa empresa(Long id, Long grupoId) {
        Empresa e = new Empresa();
        e.setId(id);
        e.setRazonSocial("Empresa " + id);
        e.setGrupoId(grupoId);
        return e;
    }

    private static Producto producto(Long id, String codigo) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo(codigo);
        p.setNombre("Producto " + codigo);
        p.setActivo(true);
        return p;
    }

    /** Traslado EN_TRANSITO de A a B con dos líneas: 10 y 4 unidades. */
    private TrasladoGrupo traslado() {
        TrasladoGrupo t = new TrasladoGrupo();
        t.setId(1L);
        t.setGrupoId(2L);
        t.setConsecutivo("TG-000001");
        t.setEmpresaOrigenId(A);
        t.setEmpresaDestinoId(B);
        t.setBodegaOrigenId(5L);
        t.setEnviadoPorUsuarioId(3L);
        t.agregarLinea(linea(100L, 41L, 51L, "10", "7.5"));
        t.agregarLinea(linea(101L, 42L, 51L, "4", "2"));
        when(traslados.deLaEmpresaBloqueado(1L, A)).thenReturn(Optional.of(t));
        when(traslados.deLaEmpresaBloqueado(1L, B)).thenReturn(Optional.of(t));
        when(traslados.deLaEmpresa(1L, A)).thenReturn(Optional.of(t));
        return t;
    }

    private static TrasladoGrupoLinea linea(Long id, Long origen, Long destino, String cantidad, String costo) {
        TrasladoGrupoLinea l = new TrasladoGrupoLinea();
        l.setId(id);
        l.setProductoOrigenId(origen);
        l.setProductoDestinoId(destino);
        l.setCantidad(new BigDecimal(cantidad));
        l.setCostoUnitario(new BigDecimal(costo));
        return l;
    }

    private static RecibirTrasladoRequest recibo(String q1, String q2) {
        return new RecibirTrasladoRequest(8L, List.of(new LineaRecepcionTrasladoRequest(100L, new BigDecimal(q1)),
                new LineaRecepcionTrasladoRequest(101L, new BigDecimal(q2))), "Llegó una caja abierta");
    }

    @Test
    void losDestinosSonLasOtrasEmpresasDelGrupo() {
        EmpresaContexto.fijar(A);
        when(empresas.findByGrupoIdOrderByIdAsc(2L)).thenReturn(List.of(empresa(A, 2L), empresa(B, 2L)));

        var destinos = service.destinos();

        assertEquals(1, destinos.size());
        assertEquals(B, destinos.get(0).id());
    }

    @Test
    void sinGrupoNoHayDestinos() {
        EmpresaContexto.fijar(A);
        when(empresas.findById(A)).thenReturn(Optional.of(empresa(A, null)));

        assertTrue(service.destinos().isEmpty());
    }

    @Test
    void enviarConProductosSinParejaDa409ConLaLista() {
        EmpresaContexto.fijar(A);
        Empresa destino = empresa(B, 2L);
        List<Referencia> refs = List.of(new Referencia(41L, "TOR-1", null, BigDecimal.TEN, BigDecimal.ONE),
                new Referencia(42L, "CLA-9", null, BigDecimal.ONE, BigDecimal.ONE));
        when(pasos.exigirDestinoDelGrupo(B)).thenReturn(destino);
        when(pasos.referenciasOrigen(eq(5L), any())).thenReturn(refs);
        when(pasos.emparejar(refs)).thenReturn(java.util.Arrays.asList(51L, null));

        NegocioException e = assertThrows(NegocioException.class, () -> service.enviar(new EnviarTrasladoRequest(B, 5L,
                List.of(new LineaEnvioRequest(41L, BigDecimal.TEN), new LineaEnvioRequest(42L, BigDecimal.ONE)), null)));

        assertEquals("PRODUCTOS_SIN_PAREJA", e.getCodigo());
        assertTrue(e.getMessage().contains("CLA-9"));
        assertTrue(!e.getMessage().contains("TOR-1"));
        verify(pasos, never()).registrarEnvio(any(), any(), any(), any());
    }

    @Test
    void enviarAUnaEmpresaDeOtroGrupoEs404YNoMuevePasosSiguientes() {
        EmpresaContexto.fijar(A);
        when(pasos.exigirDestinoDelGrupo(99L)).thenThrow(new NoEncontradoException("Empresa", 99L));

        assertThrows(NoEncontradoException.class, () -> service.enviar(new EnviarTrasladoRequest(99L, 5L,
                List.of(new LineaEnvioRequest(41L, BigDecimal.TEN)), null)));

        verify(pasos, never()).referenciasOrigen(anyLong(), any());
    }

    @Test
    void recibirConFaltanteEntraLoRecibidoAlCostoDeLaLineaYQuedaElFaltante() {
        EmpresaContexto.fijar(B);
        TrasladoGrupo t = traslado();

        TrasladoDetalleResponse r = service.recibir(1L, recibo("10", "3"));

        assertEquals(EstadoTrasladoGrupo.RECIBIDO, t.getEstado());
        assertEquals(8L, t.getBodegaDestinoId());
        assertEquals(0, BigDecimal.ONE.compareTo(r.lineas().get(1).faltante()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.lineas().get(0).faltante()));
        assertEquals("Llegó una caja abierta", r.notaRecepcion());
        verify(costos).registrarCompra(productoB, new BigDecimal("10.0000"), new BigDecimal("7.5"));
        verify(costos).registrarCompra(productoB, new BigDecimal("3.0000"), new BigDecimal("2"));
        verify(movimientos).registrarEntradaTrasladoGrupo(51L, 8L, new BigDecimal("10.0000"), new BigDecimal("7.5"), null, 1L);
        verify(movimientos).registrarEntradaTrasladoGrupo(51L, 8L, new BigDecimal("3.0000"), new BigDecimal("2"), null, 1L);
        verify(auditoria).exito(eq(AccionAuditoria.TRASLADO_GRUPO_RECIBIR), any(), eq(1L), any());
    }

    @Test
    void recibirTodoComoFaltanteNoMueveStock() {
        EmpresaContexto.fijar(B);
        TrasladoGrupo t = traslado();

        service.recibir(1L, recibo("0", "0"));

        assertEquals(EstadoTrasladoGrupo.RECIBIDO, t.getEstado());
        verify(movimientos, never()).registrarEntradaTrasladoGrupo(anyLong(), anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void laOrigenNoPuedeRecibir() {
        EmpresaContexto.fijar(A);
        traslado();

        NegocioException e = assertThrows(NegocioException.class, () -> service.recibir(1L, recibo("10", "4")));

        assertEquals("SOLO_DESTINO_RECIBE", e.getCodigo());
        verify(movimientos, never()).registrarEntradaTrasladoGrupo(anyLong(), anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void unTrasladoYaRecibidoOAnuladoNoSeRecibe() {
        EmpresaContexto.fijar(B);
        TrasladoGrupo t = traslado();
        for (EstadoTrasladoGrupo estado : List.of(EstadoTrasladoGrupo.RECIBIDO, EstadoTrasladoGrupo.ANULADO)) {
            t.setEstado(estado);
            NegocioException e = assertThrows(NegocioException.class, () -> service.recibir(1L, recibo("10", "4")));
            assertEquals("TRASLADO_NO_EN_TRANSITO", e.getCodigo());
        }
        verify(movimientos, never()).registrarEntradaTrasladoGrupo(anyLong(), anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void siSeSacoUnaEmpresaDelGrupoNoSeRecibe() {
        EmpresaContexto.fijar(B);
        traslado();
        when(empresas.findById(A)).thenReturn(Optional.of(empresa(A, null)));

        NegocioException e = assertThrows(NegocioException.class, () -> service.recibir(1L, recibo("10", "4")));

        assertEquals("TRASLADO_FUERA_DEL_GRUPO", e.getCodigo());
    }

    @Test
    void lasCantidadesRecibidasDebenCuadrarConLasLineas() {
        EmpresaContexto.fijar(B);
        traslado();

        assertEquals("CANTIDAD_INVALIDA", assertThrows(NegocioException.class,
                () -> service.recibir(1L, recibo("11", "4"))).getCodigo());
        assertEquals("CANTIDAD_INVALIDA", assertThrows(NegocioException.class,
                () -> service.recibir(1L, recibo("-1", "4"))).getCodigo());
        // falta una línea
        assertEquals("LINEAS_INCOMPLETAS", assertThrows(NegocioException.class, () -> service.recibir(1L,
                new RecibirTrasladoRequest(8L, List.of(new LineaRecepcionTrasladoRequest(100L, BigDecimal.ONE)), null))).getCodigo());
        // línea repetida o ajena
        assertEquals("LINEAS_INCOMPLETAS", assertThrows(NegocioException.class, () -> service.recibir(1L,
                new RecibirTrasladoRequest(8L, List.of(new LineaRecepcionTrasladoRequest(100L, BigDecimal.ONE),
                        new LineaRecepcionTrasladoRequest(100L, BigDecimal.ONE)), null))).getCodigo());
        assertEquals("LINEAS_INCOMPLETAS", assertThrows(NegocioException.class, () -> service.recibir(1L,
                new RecibirTrasladoRequest(8L, List.of(new LineaRecepcionTrasladoRequest(100L, BigDecimal.ONE),
                        new LineaRecepcionTrasladoRequest(999L, BigDecimal.ONE)), null))).getCodigo());
        verify(movimientos, never()).registrarEntradaTrasladoGrupo(anyLong(), anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void unaBodegaQueNoEsDeLaDestinoEs404() {
        EmpresaContexto.fijar(B);
        traslado();
        when(bodegas.findById(77L)).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> service.recibir(1L, new RecibirTrasladoRequest(77L,
                List.of(new LineaRecepcionTrasladoRequest(100L, BigDecimal.ONE), new LineaRecepcionTrasladoRequest(101L, BigDecimal.ONE)),
                null)));
    }

    @Test
    void unTrasladoDeOtrasEmpresasEs404EnTodasLasOperaciones() {
        EmpresaContexto.fijar(99L);
        sesion("ADMIN_SISTEMA");
        when(traslados.deLaEmpresaBloqueado(anyLong(), eq(99L))).thenReturn(Optional.empty());
        when(traslados.deLaEmpresa(anyLong(), eq(99L))).thenReturn(Optional.empty());

        assertThrows(NoEncontradoException.class, () -> service.recibir(1L, recibo("1", "1")));
        assertThrows(NoEncontradoException.class, () -> service.anular(1L, new AnularTrasladoRequest("x")));
        assertThrows(NoEncontradoException.class, () -> service.obtener(1L));
    }

    @Test
    void anularDevuelveElStockALaOrigenYNoDejaRecibirDespues() {
        EmpresaContexto.fijar(A);
        TrasladoGrupo t = traslado();
        Producto p41 = producto(41L, "TOR-1");
        Producto p42 = producto(42L, "TOR-2");
        when(productos.findById(41L)).thenReturn(Optional.of(p41));
        when(productos.findById(42L)).thenReturn(Optional.of(p42));
        when(productos.findAllById(any())).thenReturn(List.of(p41, p42));

        TrasladoDetalleResponse r = service.anular(1L, new AnularTrasladoRequest("  Se equivocó de destino "));

        assertEquals(EstadoTrasladoGrupo.ANULADO, t.getEstado());
        assertEquals("Se equivocó de destino", t.getMotivoAnulacion());
        assertEquals("TOR-1", r.lineas().get(0).codigo());
        verify(costos).registrarCompra(p41, new BigDecimal("10"), new BigDecimal("7.5"));
        verify(movimientos).registrarEntradaTrasladoGrupo(eq(41L), eq(5L), eq(new BigDecimal("10")),
                eq(new BigDecimal("7.5")), any(), eq(1L));
        verify(movimientos).registrarEntradaTrasladoGrupo(eq(42L), eq(5L), eq(new BigDecimal("4")),
                eq(new BigDecimal("2")), any(), eq(1L));
        verify(auditoria).exito(eq(AccionAuditoria.TRASLADO_GRUPO_ANULAR), any(), eq(1L), any());
        // ya anulado: ni se recibe ni se vuelve a anular
        EmpresaContexto.fijar(B);
        assertEquals("TRASLADO_NO_EN_TRANSITO", assertThrows(NegocioException.class,
                () -> service.recibir(1L, recibo("10", "4"))).getCodigo());
        EmpresaContexto.fijar(A);
        assertEquals("TRASLADO_NO_EN_TRANSITO", assertThrows(NegocioException.class,
                () -> service.anular(1L, new AnularTrasladoRequest("otra vez"))).getCodigo());
    }

    @Test
    void laDestinoNoPuedeAnular() {
        EmpresaContexto.fijar(B);
        traslado();

        NegocioException e = assertThrows(NegocioException.class, () -> service.anular(1L, new AnularTrasladoRequest("x")));

        assertEquals("SOLO_ORIGEN_ANULA", e.getCodigo());
        verify(movimientos, never()).registrarEntradaTrasladoGrupo(anyLong(), anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    void unTrasladoYaRecibidoNoSeAnula() {
        EmpresaContexto.fijar(A);
        traslado().setEstado(EstadoTrasladoGrupo.RECIBIDO);

        assertEquals("TRASLADO_NO_EN_TRANSITO", assertThrows(NegocioException.class,
                () -> service.anular(1L, new AnularTrasladoRequest("x"))).getCodigo());
    }

    @Test
    void sinNingunPermisoDeTrasladosNoSeConsulta() {
        EmpresaContexto.fijar(A);
        sesion("CAJERO");
        traslado();

        assertThrows(AccesoDenegadoException.class, () -> service.obtener(1L));
        assertThrows(AccesoDenegadoException.class, () -> service.listar(org.springframework.data.domain.PageRequest.of(0, 10)));
        verify(traslados, never()).deLaEmpresa(anyLong(), anyLong());
    }

    @Test
    void elDetalleMuestraLosProductosDeLaEmpresaQueConsulta() {
        EmpresaContexto.fijar(A);
        traslado();
        Producto p41 = producto(41L, "TOR-1");
        Producto p42 = producto(42L, "TOR-2");
        when(productos.findAllById(any())).thenReturn(List.of(p41, p42));

        TrasladoDetalleResponse r = service.obtener(1L);

        assertEquals("ENVIADO", r.resumen().sentido());
        assertEquals(5L, r.bodegaPropiaId());
        assertEquals(2, r.lineas().size());
        assertEquals(41L, r.lineas().get(0).productoId());
        assertEquals(null, r.lineas().get(0).faltante());
        verify(traslados, never()).deLaEmpresaBloqueado(anyLong(), anyLong());
        verify(notificaciones, never()).notificarRoles(any(), any(), isNull());
    }
}
