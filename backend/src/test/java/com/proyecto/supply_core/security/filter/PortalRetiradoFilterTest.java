package com.proyecto.supply_core.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.proyecto.supply_core.common.exception.RecursoRetiradoException;

/** Spec 031 (D14): el portal del proveedor y su registro público quedan retirados. */
class PortalRetiradoFilterTest {

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);

    @Test
    void conElPortalRetiradoTodoElPortalYElRegistroResponden410() throws Exception {
        PortalRetiradoFilter filtro = new PortalRetiradoFilter(false, resolver);
        for (String ruta : new String[] { "/api/portal/rfqs", "/api/portal/registro/empresa-b",
                "/api/portal/ordenes/1/aceptar" }) {
            MockFilterChain cadena = new MockFilterChain();
            filtro.doFilter(new MockHttpServletRequest("POST", ruta), new MockHttpServletResponse(), cadena);
            assertNull(cadena.getRequest());
        }
        verify(resolver, org.mockito.Mockito.times(3)).resolveException(any(), any(), isNull(),
                argThat(e -> e instanceof RecursoRetiradoException r && "PORTAL_RETIRADO".equals(r.getCodigo())));
    }

    @Test
    void elRestoDeLaApiYElEnlacePublicoSiguenNormales() throws Exception {
        PortalRetiradoFilter filtro = new PortalRetiradoFilter(false, resolver);
        for (String ruta : new String[] { "/api/rfqs", "/api/publico/cotizar/1.abc", "/api/portales" }) {
            MockFilterChain cadena = new MockFilterChain();
            filtro.doFilter(new MockHttpServletRequest("GET", ruta), new MockHttpServletResponse(), cadena);
            assertNotNull(cadena.getRequest());
        }
    }

    @Test
    void conElPortalHabilitadoNoSeToca() throws Exception {
        PortalRetiradoFilter filtro = new PortalRetiradoFilter(true, resolver);
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(new MockHttpServletRequest("GET", "/api/portal/rfqs"), new MockHttpServletResponse(), cadena);
        assertEquals(true, cadena.getRequest() != null);
    }
}
