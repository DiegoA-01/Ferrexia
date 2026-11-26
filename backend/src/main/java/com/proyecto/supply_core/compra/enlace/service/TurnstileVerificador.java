package com.proyecto.supply_core.compra.enlace.service;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.proyecto.supply_core.common.exception.NegocioException;

/**
 * Verificación anti-bot con Cloudflare Turnstile por HTTP, sin dependencias nuevas (spec 031). El navegador resuelve
 * el reto y manda un token; aquí se confirma con {@code siteverify} usando la clave secreta {@code TURNSTILE_SECRET}.
 * Sin clave se omite con un aviso (desarrollo), salvo que sea obligatoria (perfil {@code prod}).
 */
@Component
public class TurnstileVerificador {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerificador.class);
    private static final String URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secreto;
    private final String claveSitio;
    private final boolean obligatorio;
    private final RestClient http;

    /**
     * @param secreto     clave secreta de Turnstile (vacía = sin verificación)
     * @param claveSitio  clave pública que usa el widget del navegador
     * @param obligatorio si sin clave secreta se debe rechazar el envío
     */
    @Autowired
    public TurnstileVerificador(@Value("${app.turnstile.secret:}") String secreto,
            @Value("${app.turnstile.site-key:}") String claveSitio,
            @Value("${app.turnstile.obligatorio:false}") boolean obligatorio) {
        this(secreto, claveSitio, obligatorio, cliente());
    }

    /**
     * Constructor con el cliente HTTP (pruebas).
     *
     * @param secreto     clave secreta
     * @param claveSitio  clave pública
     * @param obligatorio si sin clave secreta se rechaza
     * @param http        cliente HTTP
     */
    TurnstileVerificador(String secreto, String claveSitio, boolean obligatorio, RestClient http) {
        this.secreto = secreto == null ? "" : secreto.trim();
        this.claveSitio = claveSitio == null ? "" : claveSitio.trim();
        this.obligatorio = obligatorio;
        this.http = http;
        if (this.secreto.isEmpty()) {
            if (obligatorio) {
                log.error("TURNSTILE_SECRET no definido: no se aceptarán cotizaciones desde enlaces");
            } else {
                log.warn("TURNSTILE_SECRET no definido: los enlaces de cotización se envían sin verificación anti-bot");
            }
        }
    }

    /**
     * Clave pública para el widget (vacía si no está configurado).
     *
     * @return clave del sitio
     */
    public String claveSitio() {
        return claveSitio;
    }

    /**
     * Confirma el token del navegador.
     *
     * @param token token del widget
     * @param ip    IP del cliente
     * @throws NegocioException {@code ANTIBOT_NO_CONFIGURADO} si es obligatorio y falta la clave, o
     *                          {@code VERIFICACION_ANTIBOT} si Cloudflare no lo confirma
     */
    public void verificar(String token, String ip) {
        if (secreto.isEmpty()) {
            if (obligatorio) {
                throw new NegocioException("ANTIBOT_NO_CONFIGURADO",
                        "La verificación anti-bot no está configurada; avise a la empresa");
            }
            return;
        }
        if (token == null || token.isBlank()) {
            throw rechazo();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secreto);
        form.add("response", token);
        if (ip != null) {
            form.add("remoteip", ip);
        }
        Map<?, ?> respuesta;
        try {
            respuesta = http.post().uri(URL).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(Map.class);
        } catch (RestClientException e) {
            log.warn("No se pudo verificar Turnstile: {}", e.getMessage());
            throw rechazo();
        }
        if (respuesta == null || !Boolean.TRUE.equals(respuesta.get("success"))) {
            throw rechazo();
        }
    }

    /**
     * Error cuando el token no pasa.
     *
     * @return excepción 409
     */
    private static NegocioException rechazo() {
        return new NegocioException("VERIFICACION_ANTIBOT",
                "No pudimos verificar que no es un robot; recargue la página e intente de nuevo");
    }

    /**
     * Cliente con tiempos cortos.
     *
     * @return cliente HTTP
     */
    private static RestClient cliente() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(fabrica).build();
    }
}
