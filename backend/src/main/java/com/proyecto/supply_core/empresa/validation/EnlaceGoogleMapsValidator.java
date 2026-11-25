package com.proyecto.supply_core.empresa.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida {@link EnlaceGoogleMaps}: esquema {@code https}, sin usuario ni puerto, y uno de los anfitriones de Google
 * Maps. Así el enlace que se muestra en "Ver en el mapa" nunca lleva a otro sitio ni ejecuta código
 * ({@code javascript:}).
 */
public class EnlaceGoogleMapsValidator implements ConstraintValidator<EnlaceGoogleMaps, String> {

    /**
     * @param valor   enlace escrito
     * @param context contexto de validación
     * @return {@code true} si está vacío o es un enlace de Google Maps
     */
    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        if (valor == null || valor.isBlank()) {
            return true;
        }
        return esEnlaceDeGoogleMaps(valor.trim());
    }

    /**
     * @param enlace enlace sin espacios alrededor
     * @return {@code true} si es {@code https} y su anfitrión y ruta son de Google Maps
     */
    static boolean esEnlaceDeGoogleMaps(String enlace) {
        URI uri;
        try {
            uri = new URI(enlace);
        } catch (URISyntaxException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getPort() != -1) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String ruta = uri.getPath() == null ? "" : uri.getPath();
        return switch (host) {
            case "maps.google.com", "maps.app.goo.gl" -> true;
            case "google.com", "www.google.com" -> ruta.startsWith("/maps");
            case "goo.gl" -> ruta.startsWith("/maps/");
            default -> false;
        };
    }
}
