package com.proyecto.supply_core.inventario.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.proyecto.supply_core.inventario.dto.AtributoVariante;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lectura y escritura de los JSON de variantes de {@code producto} (spec 028): los atributos del agrupador y la
 * combinación de cada variante. También arma el nombre completo de una variante y las combinaciones posibles.
 */
public final class VariantesJson {

    /** Separador entre el nombre del producto general y los valores de la variante. */
    public static final String SEPARADOR = " · ";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<AtributoVariante>> ATRIBUTOS = new TypeReference<>() { };
    private static final TypeReference<LinkedHashMap<String, String>> COMBINACION = new TypeReference<>() { };

    /** Solo métodos estáticos. */
    private VariantesJson() {
    }

    /**
     * @param json atributos guardados o {@code null}
     * @return lista de atributos (vacía si no hay)
     */
    public static List<AtributoVariante> atributos(String json) {
        return json == null || json.isBlank() ? List.of() : JSON.readValue(json, ATRIBUTOS);
    }

    /**
     * @param atributos atributos con sus valores
     * @return JSON para guardar
     */
    public static String atributos(List<AtributoVariante> atributos) {
        return JSON.writeValueAsString(atributos);
    }

    /**
     * @param json combinación guardada o {@code null}
     * @return valor por atributo en el orden de los atributos ({@code null} si no es variante)
     */
    public static Map<String, String> combinacion(String json) {
        return json == null || json.isBlank() ? null : JSON.readValue(json, COMBINACION);
    }

    /**
     * @param combinacion valor por atributo
     * @return JSON para guardar
     */
    public static String combinacion(Map<String, String> combinacion) {
        return JSON.writeValueAsString(combinacion);
    }

    /**
     * Producto cartesiano de los valores, en el orden de los atributos.
     *
     * @param atributos atributos con sus valores
     * @return cada combinación como valor por atributo
     */
    public static List<Map<String, String>> combinaciones(List<AtributoVariante> atributos) {
        List<Map<String, String>> resultado = new ArrayList<>();
        resultado.add(new LinkedHashMap<>());
        for (AtributoVariante a : atributos) {
            List<Map<String, String>> siguiente = new ArrayList<>();
            for (Map<String, String> parcial : resultado) {
                for (String valor : a.valores()) {
                    Map<String, String> c = new LinkedHashMap<>(parcial);
                    c.put(a.nombre(), valor);
                    siguiente.add(c);
                }
            }
            resultado = siguiente;
        }
        return resultado;
    }

    /**
     * @param atributos atributos con sus valores
     * @return cuántas combinaciones salen (sin armarlas)
     */
    public static long cuantas(List<AtributoVariante> atributos) {
        long n = 1;
        for (AtributoVariante a : atributos) {
            n *= a.valores().size();
        }
        return n;
    }

    /**
     * @param nombreGeneral nombre del producto general
     * @param combinacion   valor por atributo
     * @return nombre de la variante: "Tornillo drywall · 1 1/2" · Negro"
     */
    public static String nombre(String nombreGeneral, Map<String, String> combinacion) {
        return nombreGeneral.trim() + SEPARADOR + String.join(SEPARADOR, combinacion.values());
    }

    /**
     * Clave para comparar combinaciones sin distinguir mayúsculas ni espacios de los extremos.
     *
     * @param combinacion valor por atributo
     * @return clave normalizada
     */
    public static String clave(Map<String, String> combinacion) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : combinacion.entrySet()) {
            sb.append(e.getKey().trim().toLowerCase(java.util.Locale.ROOT)).append('=')
                    .append(e.getValue().trim().toLowerCase(java.util.Locale.ROOT)).append('\u0001');
        }
        return sb.toString();
    }
}
