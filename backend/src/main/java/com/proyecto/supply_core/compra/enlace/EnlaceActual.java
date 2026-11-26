package com.proyecto.supply_core.compra.enlace;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.compra.enlace.service.FirmaEnlace.EnlaceFirmado;

/**
 * Enlace de cotización de la petición pública en curso (spec 031). Lo deja el {@code AuthFilter} después de validar la
 * firma de {@code /api/publico/cotizar/{token}}.
 */
public final class EnlaceActual {

    /** Atributo de la petición con el {@link EnlaceFirmado}. */
    public static final String ATRIBUTO = "supplycore.enlace";

    private EnlaceActual() {
    }

    /**
     * Enlace de la petición.
     *
     * @return enlace firmado
     * @throws NoEncontradoException si la petición no trae un enlace válido
     */
    public static EnlaceFirmado enlace() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        Object valor = atributos == null ? null : atributos.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST);
        if (valor instanceof EnlaceFirmado e) {
            return e;
        }
        throw CotizacionPublicaErrores.noValido();
    }

    /** Error único para todo enlace que no sirve. */
    public static final class CotizacionPublicaErrores {

        private CotizacionPublicaErrores() {
        }

        /**
         * El mismo 404 para un enlace vencido, usado o inventado (no se dice cuál).
         *
         * @return excepción 404
         */
        public static NoEncontradoException noValido() {
            return new NoEncontradoException("El enlace no es válido o ya no está disponible");
        }
    }
}
