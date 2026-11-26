package com.proyecto.supply_core.compra.enlace.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.proyecto.supply_core.common.exception.NegocioException;

/** Spec 031: verificación anti-bot del enlace de cotización con Cloudflare Turnstile. */
class TurnstileVerificadorTest {

    private static final String URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();

    @Test
    void elTokenQueCloudflareConfirmaPasa() {
        servidor.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of("secret", "s3cr3t", "response", "tok")))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        TurnstileVerificador v = new TurnstileVerificador("s3cr3t", "clave", true, builder.build());
        assertDoesNotThrow(() -> v.verificar("tok", "1.1.1.1"));
        assertEquals("clave", v.claveSitio());
        servidor.verify();
    }

    @Test
    void elTokenRechazadoVacioOSinRespuestaNoPasan() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL)).andRespond(withServerError());
        TurnstileVerificador v = new TurnstileVerificador("s3cr3t", "clave", false, builder.build());
        assertEquals("VERIFICACION_ANTIBOT", assertThrows(NegocioException.class, () -> v.verificar("x", null)).getCodigo());
        assertEquals("VERIFICACION_ANTIBOT", assertThrows(NegocioException.class, () -> v.verificar("y", null)).getCodigo());
        assertEquals("VERIFICACION_ANTIBOT", assertThrows(NegocioException.class, () -> v.verificar(" ", null)).getCodigo());
    }

    @Test
    void sinClaveSeOmiteEnDesarrolloYSeRechazaSiEsObligatoria() {
        assertDoesNotThrow(() -> new TurnstileVerificador("", "", false, builder.build()).verificar(null, null));
        TurnstileVerificador prod = new TurnstileVerificador(null, null, true, builder.build());
        assertEquals("ANTIBOT_NO_CONFIGURADO",
                assertThrows(NegocioException.class, () -> prod.verificar("tok", null)).getCodigo());
    }
}
