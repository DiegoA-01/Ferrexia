package com.proyecto.supply_core.security;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recorre por reflexión todos los controladores de la aplicación y entrega cada endpoint con la anotación de acceso
 * que lo rige (la del método, o si no la de la clase), igual que lo hace {@code RolInterceptor}. Sirve para comparar el
 * acceso de antes y de después de la spec 022.
 */
public final class MapaAcceso {

    private static final String PAQUETE = "com.proyecto.supply_core";

    /** Clase de utilidades: no se instancia. */
    private MapaAcceso() {
    }

    /**
     * @param anotacion anotación de acceso que se busca (por ejemplo {@code RequierePermiso})
     * @param valor     cómo convertir la anotación encontrada en texto
     * @param <A>       tipo de la anotación
     * @return por cada endpoint ({@code Clase#metodo(tipos)}), el texto de su regla o {@code "-"} si no tiene; termina en
     *         {@code |I} si además exige ser usuario interno
     */
    public static <A extends Annotation> Map<String, String> endpoints(Class<A> anotacion,
            java.util.function.Function<A, String> valor) {
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Map<String, String> mapa = new TreeMap<>();
        Class<? extends Annotation> interno = cargar("com.proyecto.supply_core.security.annotation.UsuarioInterno");
        for (var bd : escaner.findCandidateComponents(PAQUETE)) {
            Class<?> clase = ClassUtils.resolveClassName(bd.getBeanClassName(), MapaAcceso.class.getClassLoader());
            for (Method m : clase.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class) == null) {
                    continue;
                }
                A regla = AnnotatedElementUtils.findMergedAnnotation(m, anotacion);
                if (regla == null) {
                    regla = AnnotatedElementUtils.findMergedAnnotation(clase, anotacion);
                }
                boolean soloInternos = AnnotatedElementUtils.findMergedAnnotation(m, interno) != null
                        || AnnotatedElementUtils.findMergedAnnotation(clase, interno) != null;
                String clave = clase.getSimpleName() + "#" + m.getName() + "("
                        + Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(","))
                        + ")";
                mapa.put(clave, (regla == null ? "-" : valor.apply(regla)) + (soloInternos ? "|I" : ""));
            }
        }
        return mapa;
    }

    /**
     * @param nombre clase de anotación
     * @return la clase
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> cargar(String nombre) {
        return (Class<? extends Annotation>) ClassUtils.resolveClassName(nombre, MapaAcceso.class.getClassLoader());
    }
}
