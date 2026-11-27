package com.proyecto.supply_core.security.context;

import java.util.Optional;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;

/**
 * Acceso al super administrador de grupo de la petición actual: {@code GrupoActual.usuario().grupoId()}. Lo llena el
 * {@code AuthFilter} en las rutas {@code /api/grupo/**}. Es independiente de {@link SesionActual} y de
 * {@link PlataformaActual}.
 */
public final class GrupoActual {

    /** Nombre del atributo de request donde el filtro guarda el {@link GrupoAutenticado}. */
    public static final String ATRIBUTO = "supplycore.grupo";

    /** Clase utilitaria: no se instancia. */
    private GrupoActual() {
    }

    /**
     * Devuelve el super administrador de grupo autenticado.
     *
     * @return usuario de la petición
     * @throws NoAutenticadoException si la petición no tiene sesión de grupo
     */
    public static GrupoAutenticado usuario() {
        return opcional().orElseThrow(() -> new NoAutenticadoException("Debe iniciar sesión"));
    }

    /**
     * Devuelve el super administrador de grupo si existe.
     *
     * @return usuario o vacío
     */
    public static Optional<GrupoAutenticado> opcional() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((GrupoAutenticado) attrs.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST));
    }
}
