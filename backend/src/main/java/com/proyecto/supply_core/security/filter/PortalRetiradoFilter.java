package com.proyecto.supply_core.security.filter;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.proyecto.supply_core.common.exception.RecursoRetiradoException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Portal del proveedor retirado (spec 031, D14): todo {@code /api/portal/**} (incluido el registro público) responde
 * 410 {@code PORTAL_RETIRADO}. El proveedor cotiza con un enlace y compras registra sus respuestas a la orden. Se puede
 * volver a prender con {@code PORTAL_HABILITADO=true}; los datos del portal se conservan.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class PortalRetiradoFilter extends OncePerRequestFilter {

    private static final String PREFIJO_PORTAL = "/api/portal/";

    private final boolean habilitado;
    private final HandlerExceptionResolver exceptionResolver;

    /**
     * @param habilitado        si el portal sigue funcionando
     * @param exceptionResolver resolver de Spring MVC que invoca al {@code @RestControllerAdvice}
     */
    public PortalRetiradoFilter(@Value("${app.portal.habilitado:false}") boolean habilitado,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.habilitado = habilitado;
        this.exceptionResolver = exceptionResolver;
    }

    /**
     * Solo actúa sobre el portal y cuando está retirado.
     *
     * @param request petición entrante
     * @return {@code true} si la petición sigue normal
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return habilitado || !ruta.startsWith(PREFIJO_PORTAL) || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    /**
     * Responde 410 sin continuar.
     *
     * @param request  petición entrante
     * @param response respuesta
     * @param chain    resto de la cadena (no se usa)
     * @throws ServletException nunca
     * @throws IOException      nunca
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        exceptionResolver.resolveException(request, response, null, new RecursoRetiradoException("PORTAL_RETIRADO",
                "El portal del proveedor ya no está disponible: la empresa le envía un enlace para cotizar"));
    }
}
