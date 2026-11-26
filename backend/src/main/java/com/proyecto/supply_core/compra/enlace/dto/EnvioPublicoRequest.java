package com.proyecto.supply_core.compra.enlace.dto;

import jakarta.validation.constraints.Size;

/**
 * Envío de la cotización desde el enlace (spec 031).
 *
 * @param turnstileToken token del widget anti-bot
 * @param sitioWeb       campo trampa, oculto para las personas: si llega con valor, lo llenó un bot
 */
public record EnvioPublicoRequest(@Size(max = 4096, message = "máximo 4096 caracteres") String turnstileToken,
        @Size(max = 500, message = "máximo 500 caracteres") String sitioWeb) {
}
