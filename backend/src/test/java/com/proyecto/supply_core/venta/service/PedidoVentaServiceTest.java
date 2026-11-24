package com.proyecto.supply_core.venta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.cliente.repository.ClienteRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.venta.dto.PedidoVentaResponse;
import com.proyecto.supply_core.venta.entity.PedidoVenta;
import com.proyecto.supply_core.venta.enums.EstadoPedidoVenta;
import com.proyecto.supply_core.venta.repository.PedidoVentaRepository;

/** Descarte de pedidos de venta con quién, cuándo y motivo (spec 016). */
@ExtendWith(MockitoExtension.class)
class PedidoVentaServiceTest {

    @Mock private PedidoVentaRepository pedidoRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private UnidadMedidaRepository unidadRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private VentaService ventaService;
    @Mock private PreciosService preciosService;
    @Mock private ConsecutivoGenerator consecutivos;
    @Mock private AuditoriaService auditoriaService;

    private PedidoVentaService servicio;

    /** Arma el servicio y deja en la sesión a un vendedor (id 7). */
    @BeforeEach
    void crear() {
        servicio = new PedidoVentaService(pedidoRepository, productoRepository, unidadRepository, clienteRepository,
                usuarioRepository, ventaService, preciosService, consecutivos, auditoriaService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(7L, "vendedor@x.com", "Vendedor", Set.of("VENDEDOR"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    /** Limpia la sesión del hilo. */
    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * @param estado estado del pedido
     * @return pedido del vendedor 7 con cliente 3
     */
    private static PedidoVenta pedido(EstadoPedidoVenta estado) {
        PedidoVenta p = new PedidoVenta();
        p.setId(15L);
        p.setConsecutivo("PV-000015");
        p.setClienteId(3L);
        p.setVendedorUsuarioId(7L);
        p.setEstado(estado);
        return p;
    }

    /** Descartar guarda quién (la sesión), cuándo y el motivo, y lo deja en la auditoría. */
    @Test
    void descartarGuardaQuienCuandoYMotivo() {
        PedidoVenta p = pedido(EstadoPedidoVenta.PENDIENTE_COBRO);
        when(pedidoRepository.bloquear(15L)).thenReturn(Optional.of(p));
        Usuario vendedor = mock(Usuario.class);
        when(vendedor.getNombreCompleto()).thenReturn("Vendedor");
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(vendedor));

        PedidoVentaResponse r = servicio.descartar(15L, "  El cliente desistió  ");

        assertThat(p.getEstado()).isEqualTo(EstadoPedidoVenta.DESCARTADO);
        assertThat(p.getDescartadoPorUsuarioId()).isEqualTo(7L);
        assertThat(p.getDescartadoEn()).isNotNull();
        assertThat(p.getMotivoDescarte()).isEqualTo("El cliente desistió");
        assertThat(r.descartadoPor()).isEqualTo("Vendedor");
        assertThat(r.motivoDescarte()).isEqualTo("El cliente desistió");
        verify(auditoriaService).exito(eq(AccionAuditoria.PEDIDO_VENTA_DESCARTAR), eq(EntidadAuditada.PEDIDO_VENTA),
                eq(15L), eq("PV-000015 · motivo: El cliente desistió"));
    }

    /** Un pedido que ya no está pendiente no se descarta otra vez ni cambia su motivo. */
    @Test
    void unPedidoYaDescartadoNoSeVuelveADescartar() {
        PedidoVenta p = pedido(EstadoPedidoVenta.DESCARTADO);
        when(pedidoRepository.bloquear(15L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> servicio.descartar(15L, "Otra vez")).isInstanceOf(NegocioException.class);
        assertThat(p.getMotivoDescarte()).isNull();
        verify(auditoriaService, never()).exito(any(), any(), any(), any());
    }
}
