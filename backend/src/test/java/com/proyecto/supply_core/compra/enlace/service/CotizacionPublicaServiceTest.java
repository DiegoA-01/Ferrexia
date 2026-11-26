package com.proyecto.supply_core.compra.enlace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.compra.cotizacion.service.CotizacionService;
import com.proyecto.supply_core.compra.enlace.dto.EnvioPublicoRequest;
import com.proyecto.supply_core.compra.enlace.entity.EnlaceCotizacion;
import com.proyecto.supply_core.compra.enlace.repository.EnlaceCotizacionRepository;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;

/** Spec 031: cotizar desde el enlace (vigencia, campo trampa, anti-bot y cierre del enlace). */
class CotizacionPublicaServiceTest {

    private final EnlaceCotizacionRepository enlaces = mock(EnlaceCotizacionRepository.class);
    private final RfqRepository rfqs = mock(RfqRepository.class);
    private final CotizacionService cotizaciones = mock(CotizacionService.class);
    private final TurnstileVerificador turnstile = mock(TurnstileVerificador.class);
    private final AccesoEmpresaService acceso = mock(AccesoEmpresaService.class);
    private final CotizacionPublicaService service = new CotizacionPublicaService(enlaces, rfqs,
            mock(ProveedorRepository.class), mock(EmpresaRepository.class), cotizaciones, turnstile, acceso);

    private EnlaceCotizacion enlace;
    private Rfq rfq;

    @BeforeEach
    void enlaceVigente() {
        enlace = new EnlaceCotizacion();
        enlace.setId(5L);
        enlace.setEmpresaId(1L);
        enlace.setRfqId(10L);
        enlace.setProveedorId(40L);
        enlace.setVenceEn(LocalDateTime.now().plusDays(2));
        rfq = mock(Rfq.class);
        when(rfq.getEstado()).thenReturn(EstadoRfq.ABIERTA);
        when(rfq.getFechaLimite()).thenReturn(LocalDateTime.now().plusDays(2));
        when(enlaces.bloquear(5L)).thenReturn(Optional.of(enlace));
        when(enlaces.findById(5L)).thenReturn(Optional.of(enlace));
        when(rfqs.findById(10L)).thenReturn(Optional.of(rfq));
    }

    @Test
    void enviarVerificaElAntiBotEnviaYCierraElEnlace() {
        service.enviar(5L, new EnvioPublicoRequest("tok", null), "1.1.1.1");
        verify(turnstile).verificar("tok", "1.1.1.1");
        verify(cotizaciones).enviar(10L, 40L);
        assertNotNull(enlace.getUsadoEn());
    }

    @Test
    void elCampoTrampaSeDescartaEnSilencio() {
        service.enviar(5L, new EnvioPublicoRequest("tok", "http://spam"), "1.1.1.1");
        verify(turnstile, never()).verificar(any(), any());
        verify(cotizaciones, never()).enviar(anyLong(), anyLong());
        assertNull(enlace.getUsadoEn());
    }

    @Test
    void siElAntiBotRechazaNoSeEnviaNiSeCierra() {
        doThrow(new NegocioException("VERIFICACION_ANTIBOT", "no")).when(turnstile).verificar(any(), any());
        assertEquals("VERIFICACION_ANTIBOT", assertThrows(NegocioException.class,
                () -> service.enviar(5L, new EnvioPublicoRequest("x", null), null)).getCodigo());
        verify(cotizaciones, never()).enviar(anyLong(), anyLong());
        assertNull(enlace.getUsadoEn());
    }

    @Test
    void usadoVencidoRfqCerradaOModuloApagadoDanElMismo404() {
        String mensaje = "El enlace no es válido o ya no está disponible";
        enlace.setUsadoEn(LocalDateTime.now());
        assertEquals(mensaje, assertThrows(NoEncontradoException.class, () -> service.ver(5L)).getMessage());

        enlace.setUsadoEn(null);
        enlace.setVenceEn(LocalDateTime.now().minusMinutes(1));
        assertEquals(mensaje, assertThrows(NoEncontradoException.class, () -> service.ver(5L)).getMessage());

        enlace.setVenceEn(LocalDateTime.now().plusDays(1));
        when(rfq.getEstado()).thenReturn(EstadoRfq.CERRADA);
        assertEquals(mensaje, assertThrows(NoEncontradoException.class, () -> service.guardar(5L, null)).getMessage());

        when(rfq.getEstado()).thenReturn(EstadoRfq.ABIERTA);
        doThrow(new AccesoDenegadoException("MODULO_NO_CONTRATADO", "x")).when(acceso).exigirModulo(anyLong(),
                anyString());
        assertEquals(mensaje, assertThrows(NoEncontradoException.class,
                () -> service.enviar(5L, new EnvioPublicoRequest("t", null), null)).getMessage());

        when(enlaces.findById(6L)).thenReturn(Optional.empty());
        assertEquals(mensaje, assertThrows(NoEncontradoException.class, () -> service.ver(6L)).getMessage());
        verify(cotizaciones, never()).enviar(anyLong(), anyLong());
    }
}
