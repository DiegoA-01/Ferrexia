package com.proyecto.supply_core.empresa.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Enlace de Google Maps de la sede (spec 020). */
class EnlaceGoogleMapsValidatorTest {

    private final EnlaceGoogleMapsValidator validador = new EnlaceGoogleMapsValidator();

    @Test
    void aceptaLosEnlacesQueEntregaGoogleMapsAlCompartir() {
        for (String enlace : new String[] {
                "https://maps.app.goo.gl/AbCdEf123",
                "https://www.google.com/maps/place/Ferreter%C3%ADa/@4.6,-74.0,17z",
                "https://google.com/maps?q=4.6,-74.08",
                "https://maps.google.com/?q=Calle+10",
                "https://goo.gl/maps/xyz",
                "  https://maps.app.goo.gl/AbCdEf123  " }) {
            assertTrue(validador.isValid(enlace, null), enlace);
        }
    }

    @Test
    void vacioSeAceptaPorqueEsOpcional() {
        assertTrue(validador.isValid(null, null));
        assertTrue(validador.isValid("", null));
        assertTrue(validador.isValid("   ", null));
    }

    @Test
    void rechazaOtrosSitiosHttpYEnlacesQueEjecutanCodigo() {
        for (String enlace : new String[] {
                "http://maps.app.goo.gl/AbCdEf123",
                "https://maps.app.goo.gl.evil.com/x",
                "https://evil.com/maps",
                "https://www.google.com/search?q=ferreteria",
                "https://goo.gl/abc",
                "javascript:alert(1)",
                "https://usuario@maps.google.com/",
                "https://maps.google.com:8443/",
                "Calle 10 # 20-30",
                "https://maps google.com" }) {
            assertFalse(validador.isValid(enlace, null), enlace);
        }
    }
}
