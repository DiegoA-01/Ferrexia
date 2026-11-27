package com.proyecto.supply_core.security.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.UriUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.compra.enlace.EnlaceActual;
import com.proyecto.supply_core.compra.enlace.service.FirmaEnlace;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.grupo.service.GrupoSesionService;
import com.proyecto.supply_core.security.context.GrupoActual;
import com.proyecto.supply_core.security.context.GrupoAutenticado;
import com.proyecto.supply_core.security.context.PlataformaActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.security.service.SesionUsuarioService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exige {@code Authorization: Bearer <jwt>} en todo {@code /api/**} salvo las rutas públicas.
 * Los errores se delegan al {@code GlobalExceptionHandler} para responder el mismo {@code ErrorResponse}.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";
    /** Rutas sin token: login y autorregistro de proveedores (que no otorga acceso hasta ser aprobado). */
    private static final List<String> RUTAS_PUBLICAS = List.of("/api/auth/login", "/api/auth/refresh",
            "/api/auth/recuperar", "/api/auth/restablecer", "/api/auth/mfa", "/api/portal/registro/**",
            "/api/plataforma/auth/**", "/api/grupo/auth/**");

    /** La consola de la plataforma exige su propio token (sin empresa) y nunca el de una empresa. */
    private static final String PREFIJO_PLATAFORMA = "/api/plataforma/";

    /** La zona del super administrador de un grupo exige su propio token (sin empresa) y nunca el de una empresa. */
    private static final String PREFIJO_GRUPO = "/api/grupo/";

    /** El autorregistro lleva el código público de la empresa en la ruta: {@code /api/portal/registro/{codigo}/...}. */
    private static final String PREFIJO_REGISTRO = "/api/portal/registro/";

    /** Cotizar con un enlace, sin cuenta (spec 031): {@code /api/publico/cotizar/{token}/...}. */
    private static final String PREFIJO_ENLACE = "/api/publico/cotizar/";

    private final JwtUtil jwtUtil;
    private final EmpresaRepository empresaRepository;
    private final SesionUsuarioService sesionUsuario;
    private final FirmaEnlace firmaEnlace;
    private final GrupoSesionService grupoSesion;
    private final HandlerExceptionResolver exceptionResolver;
    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * @param jwtUtil           validador de tokens
     * @param empresaRepository resuelve la empresa del autorregistro público por su código
     * @param sesionUsuario     corta el token de quien cambió su clave o fue desactivado
     * @param firmaEnlace       valida los enlaces de cotización y dice su empresa
     * @param grupoSesion       corta el token del super administrador desactivado o de un grupo inactivo
     * @param exceptionResolver resolver de Spring MVC que invoca al {@code @RestControllerAdvice}
     */
    public AuthFilter(JwtUtil jwtUtil, EmpresaRepository empresaRepository, SesionUsuarioService sesionUsuario,
            FirmaEnlace firmaEnlace, GrupoSesionService grupoSesion,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtUtil = jwtUtil;
        this.empresaRepository = empresaRepository;
        this.sesionUsuario = sesionUsuario;
        this.firmaEnlace = firmaEnlace;
        this.grupoSesion = grupoSesion;
        this.exceptionResolver = exceptionResolver;
    }

    /**
     * Excluye lo que no es API y el preflight CORS.
     *
     * @param request petición entrante
     * @return {@code true} si el filtro no debe actuar
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = ruta(request);
        return !ruta.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    /**
     * Ruta de la petición sin el context path.
     *
     * @param request petición entrante
     * @return ruta relativa a la aplicación
     */
    private static String ruta(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /**
     * En rutas públicas trabaja sin empresa (login, renovación y registro resuelven la empresa por su cuenta);
     * en las demás valida el token, deja el usuario en la petición y fija la empresa del JWT como contexto
     * de toda la petición. Si falla responde 401 sin continuar. El contexto siempre se limpia al terminar.
     *
     * @param request  petición entrante
     * @param response respuesta
     * @param chain    resto de la cadena de filtros
     * @throws ServletException si falla un filtro posterior
     * @throws IOException      si falla la escritura de la respuesta
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            String ruta = ruta(request);
            if (ruta.startsWith(PREFIJO_REGISTRO)) {
                EmpresaContexto.fijar(empresaDelRegistro(ruta));
            } else if (ruta.startsWith(PREFIJO_ENLACE)) {
                FirmaEnlace.EnlaceFirmado enlace = enlaceDeLaRuta(ruta);
                request.setAttribute(EnlaceActual.ATRIBUTO, enlace);
                EmpresaContexto.fijar(enlace.empresaId());
            } else if (esPublica(ruta)) {
                EmpresaContexto.fijarSinEmpresa();
            } else if (ruta.startsWith(PREFIJO_PLATAFORMA)) {
                // la plataforma trabaja sin empresa: no lee datos de negocio y su token no sirve en la API de una empresa
                String header = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (header == null || !header.startsWith(PREFIJO)) {
                    throw new NoAutenticadoException("Debe iniciar sesión");
                }
                request.setAttribute(PlataformaActual.ATRIBUTO,
                        jwtUtil.validarPlataforma(header.substring(PREFIJO.length()).trim()));
                EmpresaContexto.fijarSinEmpresa();
            } else if (ruta.startsWith(PREFIJO_GRUPO)) {
                // la zona del grupo trabaja sin empresa; su token no sirve en la API de una empresa ni en la plataforma
                String header = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (header == null || !header.startsWith(PREFIJO)) {
                    throw new NoAutenticadoException("Debe iniciar sesión");
                }
                String token = header.substring(PREFIJO.length()).trim();
                GrupoAutenticado grupo = jwtUtil.validarGrupo(token);
                EmpresaContexto.fijarSinEmpresa();
                // super administrador o grupo desactivado después de emitir el token: ya no sirve
                grupoSesion.exigirVigente(grupo, jwtUtil.versionSesion(token));
                request.setAttribute(GrupoActual.ATRIBUTO, grupo);
            } else {
                String header = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (header == null || !header.startsWith(PREFIJO)) {
                    throw new NoAutenticadoException("Debe iniciar sesión");
                }
                String token = header.substring(PREFIJO.length()).trim();
                UsuarioAutenticado usuario = jwtUtil.validar(token);
                request.setAttribute(SesionActual.ATRIBUTO, usuario);
                EmpresaContexto.fijar(usuario.empresaId());
                // cuenta desactivada o clave cambiada después de emitir el token: ya no sirve, sin esperar su vencimiento
                sesionUsuario.exigirVigente(usuario.id(), jwtUtil.versionSesion(token));
            }
            chain.doFilter(request, response);
        } catch (NoAutenticadoException | NoEncontradoException ex) {
            exceptionResolver.resolveException(request, response, null, ex);
        } finally {
            EmpresaContexto.limpiar();
        }
    }

    /**
     * Empresa a la que se registra el proveedor, según el código público de la ruta. Se resuelve sin empresa
     * (es una consulta sobre la tabla de empresas) y el resto de la petición ya corre dentro de esa empresa.
     *
     * @param ruta ruta {@code /api/portal/registro/{codigo}/...}
     * @return id de la empresa
     * @throws NoEncontradoException si el código no corresponde a ninguna empresa
     */
    private Long empresaDelRegistro(String ruta) {
        String resto = ruta.substring(PREFIJO_REGISTRO.length());
        String codigo = resto.contains("/") ? resto.substring(0, resto.indexOf('/')) : resto;
        EmpresaContexto.fijarSinEmpresa();
        try {
            // el código puede llevar espacios o tildes: en la dirección llega codificado ("Don%20Mart%C3%ADn")
            codigo = UriUtils.decode(codigo, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new NoEncontradoException("La empresa indicada no existe");
        }
        return empresaRepository.idPorCodigoPublico(codigo)
                .orElseThrow(() -> new NoEncontradoException("La empresa indicada no existe"));
    }

    /**
     * Enlace de cotización de la ruta, validado por su firma (sin empresa: se lee por id). Vencido, usado o inventado
     * se revisa después, dentro de la empresa; aquí solo se rechaza el que no existe o tiene otra firma.
     *
     * @param ruta ruta {@code /api/publico/cotizar/{token}/...}
     * @return enlace firmado
     * @throws NoEncontradoException el mismo 404 de todo enlace que no sirve
     */
    private FirmaEnlace.EnlaceFirmado enlaceDeLaRuta(String ruta) {
        String resto = ruta.substring(PREFIJO_ENLACE.length());
        String token = resto.contains("/") ? resto.substring(0, resto.indexOf('/')) : resto;
        EmpresaContexto.fijarSinEmpresa();
        return firmaEnlace.validar(token).orElseThrow(EnlaceActual.CotizacionPublicaErrores::noValido);
    }

    /**
     * Indica si la ruta no exige token.
     *
     * @param ruta ruta relativa a la aplicación
     * @return {@code true} si es una ruta pública
     */
    private boolean esPublica(String ruta) {
        return RUTAS_PUBLICAS.stream().anyMatch(p -> matcher.match(p, ruta));
    }
}
