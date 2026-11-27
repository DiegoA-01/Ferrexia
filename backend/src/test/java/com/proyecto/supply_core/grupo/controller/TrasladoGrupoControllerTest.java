package com.proyecto.supply_core.grupo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.DestinoResponse;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.LineaTrasladoResponse;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.TrasladoDetalleResponse;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.TrasladoResumenResponse;
import com.proyecto.supply_core.grupo.enums.EstadoTrasladoGrupo;
import com.proyecto.supply_core.grupo.service.TrasladoGrupoService;

/** Rutas de los traslados entre empresas del grupo: permisos por rol, token y validación (spec 035). */
@WebMvcTest(TrasladoGrupoController.class)
class TrasladoGrupoControllerTest extends WebTestBase {

    @MockitoBean
    private TrasladoGrupoService service;

    private static final String ENVIO = """
            {"empresaDestinoId":11,"bodegaOrigenId":5,"lineas":[{"productoId":41,"cantidad":3}],"nota":"x"}""";
    private static final String RECIBO = """
            {"bodegaDestinoId":8,"lineas":[{"lineaId":100,"cantidadRecibida":2}]}""";

    private static TrasladoDetalleResponse detalle() {
        TrasladoResumenResponse r = new TrasladoResumenResponse(1L, "TG-000001", EstadoTrasladoGrupo.EN_TRANSITO, "ENVIADO",
                10L, "A", 11L, "B", 1, null, LocalDateTime.now(), null, null);
        return new TrasladoDetalleResponse(r, 5L, null, null, List.of(new LineaTrasladoResponse(100L, 41L, "TOR-1",
                "Tornillo", new BigDecimal("3"), null, null, new BigDecimal("7.5"))));
    }

    @Test
    void quienNoTienePermisoNoPuedeNadaYNoLlegaAlServicio() throws Exception {
        for (String rol : List.of("CAJERO", "VENDEDOR", "COMPRADOR")) {
            mvc.perform(get("/api/traslados-grupo/destinos").header(HttpHeaders.AUTHORIZATION, bearer(rol)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/traslados-grupo").header(HttpHeaders.AUTHORIZATION, bearer(rol))
                    .contentType(MediaType.APPLICATION_JSON).content(ENVIO)).andExpect(status().isForbidden());
            mvc.perform(post("/api/traslados-grupo/1/recibir").header(HttpHeaders.AUTHORIZATION, bearer(rol))
                    .contentType(MediaType.APPLICATION_JSON).content(RECIBO)).andExpect(status().isForbidden());
            mvc.perform(post("/api/traslados-grupo/1/anular").header(HttpHeaders.AUTHORIZATION, bearer(rol))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void sinTokenOConElTokenDelGrupoONoSeEntra() throws Exception {
        mvc.perform(get("/api/traslados-grupo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/traslados-grupo").header(HttpHeaders.AUTHORIZATION, bearerGrupo()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/traslados-grupo/destinos").header(HttpHeaders.AUTHORIZATION, bearerPlataforma()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/traslados-grupo").header(HttpHeaders.AUTHORIZATION, bearerProveedor(4L))
                .contentType(MediaType.APPLICATION_JSON).content(ENVIO)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void elAlmacenistaEnviaRecibeAnulaYConsulta() throws Exception {
        when(service.destinos()).thenReturn(List.of(new DestinoResponse(11L, "B")));
        when(service.enviar(any())).thenReturn(detalle());
        when(service.recibir(eq(1L), any())).thenReturn(detalle());
        when(service.anular(eq(1L), any())).thenReturn(detalle());
        when(service.obtener(1L)).thenReturn(detalle());
        when(service.listar(any(Pageable.class))).thenReturn(new PageResponse<>(List.of(detalle().resumen()), 0, 20, 1, 1));
        String auth = bearer("ALMACENISTA");

        mvc.perform(get("/api/traslados-grupo/destinos").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].nit").doesNotExist());
        mvc.perform(post("/api/traslados-grupo").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(ENVIO))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.resumen.consecutivo").value("TG-000001"));
        mvc.perform(post("/api/traslados-grupo/1/recibir").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(RECIBO)).andExpect(status().isOk());
        mvc.perform(post("/api/traslados-grupo/1/anular").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Se equivocó\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/traslados-grupo/1").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lineas[0].codigo").value("TOR-1"));
        mvc.perform(get("/api/traslados-grupo").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk());
    }

    @Test
    void losDatosSeValidanAntesDeLlegarAlServicio() throws Exception {
        String auth = bearer("ADMIN_SISTEMA");
        for (String malo : List.of("{}",
                "{\"empresaDestinoId\":11,\"bodegaOrigenId\":5,\"lineas\":[]}",
                "{\"empresaDestinoId\":11,\"bodegaOrigenId\":5,\"lineas\":[{\"productoId\":41,\"cantidad\":0}]}",
                "{\"empresaDestinoId\":0,\"bodegaOrigenId\":5,\"lineas\":[{\"productoId\":41,\"cantidad\":1}]}")) {
            mvc.perform(post("/api/traslados-grupo").header(HttpHeaders.AUTHORIZATION, auth)
                    .contentType(MediaType.APPLICATION_JSON).content(malo)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/traslados-grupo/1/recibir").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bodegaDestinoId\":8,\"lineas\":[{\"lineaId\":100,\"cantidadRecibida\":-1}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/traslados-grupo/1/anular").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/traslados-grupo/0").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test
    void losErroresDeNegocioSalenComo409Y404() throws Exception {
        when(service.enviar(any())).thenThrow(new NegocioException("PRODUCTOS_SIN_PAREJA", "No tiene: CLA-9"));
        when(service.obtener(7L)).thenThrow(new NoEncontradoException("Traslado", 7L));
        String auth = bearer("ADMIN_SISTEMA");

        mvc.perform(post("/api/traslados-grupo").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(ENVIO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PRODUCTOS_SIN_PAREJA"));
        mvc.perform(get("/api/traslados-grupo/7").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNotFound());
        verify(service).enviar(any());
    }
}
