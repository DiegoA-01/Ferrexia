package com.proyecto.supply_core.empresa.dto;

import com.proyecto.supply_core.empresa.validation.EnlaceGoogleMaps;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta o edición de una sede. El nombre único se valida en el Service (alta y edición).
 *
 * <p>Desde la spec 020 la ubicación es el enlace de Google Maps que comparte la persona (antes eran latitud y longitud).
 *
 * @param nombre     nombre visible
 * @param direccion  dirección
 * @param enlaceMapa enlace de Google Maps de la ubicación, opcional
 */
public record SedeRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "máximo 150 caracteres")
        String nombre,

        @Size(max = 250, message = "máximo 250 caracteres")
        String direccion,

        @Size(max = 500, message = "máximo 500 caracteres")
        @EnlaceGoogleMaps
        String enlaceMapa) {
}
