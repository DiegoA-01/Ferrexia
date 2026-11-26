package com.proyecto.supply_core.compra.enlace.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.compra.enlace.dto.CotizacionPublicaResponse;
import com.proyecto.supply_core.compra.enlace.dto.EnlaceResponse;
import com.proyecto.supply_core.compra.enlace.service.CotizacionPublicaService;
import com.proyecto.supply_core.compra.enlace.service.EnlaceCotizacionService;
import com.proyecto.supply_core.compra.enlace.service.FirmaEnlace.EnlaceFirmado;
import com.proyecto.supply_core.compra.orden.controller.OrdenProveedorComprasController;
import com.proyecto.supply_core.compra.orden.dto.OrdenResponse;
import com.proyecto.supply_core.compra.orden.service.OrdenProveedorService;
import com.proyecto.supply_core.exportacion.service.ExportacionService;

/** Spec 031: endpoints del enlace público, enlaces de la RFQ y pasos del proveedor registrados por compras. */
@WebMvcTest({ CotizacionPublicaController.class, EnlaceCotizacionController.class,
        OrdenProveedorComprasController.class })
class EnlacesControllerTest extends WebTestBase {

    @MockitoBean
    private CotizacionPublicaService publicaService;

    @MockitoBean
    private EnlaceCotizacionService enlaceService;

    @MockitoBean
    private OrdenProveedorService ordenProveedorService;

    @MockitoBean
    private ExportacionService exportacionService;

    @Test
    void elEnlaceValidoEntraSinSesionYElInvalidoDa404() throws Exception {
        when(firmaEnlace.validar("5.firma")).thenReturn(Optional.of(new EnlaceFirmado(5L, 1L)));
        when(firmaEnlace.validar("5.otra")).thenReturn(Optional.empty());
        when(publicaService.ver(5L)).thenReturn(new CotizacionPublicaResponse("Ferretería", "Proveedor", null, null, ""));

        mvc.perform(get("/api/publico/cotizar/5.firma")).andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa").value("Ferretería"));
        mvc.perform(get("/api/publico/cotizar/5.otra")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El enlace no es válido o ya no está disponible"));
        mvc.perform(post("/api/publico/cotizar/5.firma/enviar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"turnstileToken\":\"tok\",\"sitioWeb\":\"\"}")).andExpect(status().isNoContent());
        verify(publicaService).enviar(eq(5L), any(), any());
    }

    @Test
    void losEnlacesDeLaRfqSonSoloDeQuienGestionaRfq() throws Exception {
        when(enlaceService.listar(3L)).thenReturn(List.of(new EnlaceResponse(40L, "Prov", null, "300", "http://x/cotizar/1.a",
                "PENDIENTE", null, null)));
        mvc.perform(get("/api/rfqs/3/enlaces").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
        mvc.perform(get("/api/rfqs/3/enlaces").header(HttpHeaders.AUTHORIZATION, bearer("CAJERO")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/rfqs/3/enlaces")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/rfqs/3/enlaces/40/regenerar").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk());
        verify(enlaceService).regenerar(3L, 40L);
    }

    @Test
    void comprasRegistraLaRespuestaDelProveedorYLeEnviaLaOrden() throws Exception {
        when(ordenProveedorService.registrarPorCompras(eq(9L), eq("despachar"), any()))
                .thenReturn(org.mockito.Mockito.mock(OrdenResponse.class));
        when(exportacionService.enviarOrdenAlProveedor(9L)).thenReturn("prov@x.com");

        mvc.perform(post("/api/ordenes/9/proveedor/despachar").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"observacion\":\"Guía 1\"}"))
                .andExpect(status().isOk());
        verify(ordenProveedorService).registrarPorCompras(9L, "despachar", "Guía 1");
        mvc.perform(post("/api/ordenes/9/proveedor/volar").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ordenes/9/proveedor/aceptar").header(HttpHeaders.AUTHORIZATION, bearer("CAJERO")))
                .andExpect(status().isForbidden());
        verify(ordenProveedorService, never()).registrarPorCompras(eq(9L), eq("aceptar"), isNull());

        mvc.perform(post("/api/ordenes/9/enviar-proveedor").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.para").value("prov@x.com"));
    }
}
