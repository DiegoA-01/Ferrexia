package com.proyecto.supply_core.plataforma.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Arma el código de ingreso de una empresa a partir de su razón social (spec 020), para que el dueño de la plataforma no
 * tenga que inventarlo. Java puro y sin estado.
 *
 * <p>Ejemplo: "Ferretería El Tornillo S.A.S." → {@code ferreteria-el-tornillo}. Reglas: minúsculas, sin tildes, solo
 * letras y números unidos con guion, sin la sigla societaria del final (S.A.S., S.A., Ltda., E.U., y Cía.), máximo
 * {@value #LARGO_MAXIMO} caracteres cortando entre palabras, y al menos {@value #LARGO_MINIMO}.
 */
public final class GeneradorCodigoEmpresa {

    /** Largo máximo del código generado (el campo admite 40; se deja espacio para el sufijo {@code -N}). */
    static final int LARGO_MAXIMO = 30;
    /** Largo mínimo de un código de ingreso. */
    static final int LARGO_MINIMO = 3;
    /** Código cuando la razón social no deja nada útil. */
    static final String RESPALDO = "empresa";

    /** Siglas societarias que se quitan del final (ya sin puntos y separadas por espacios). */
    private static final List<List<String>> SIGLAS = List.of(
            List.of("s", "a", "s"), List.of("sas"), List.of("s", "a"), List.of("sa"), List.of("ltda"),
            List.of("limitada"), List.of("e", "u"), List.of("eu"), List.of("y", "cia"), List.of("cia"),
            List.of("s", "en", "c"), List.of("s", "c", "a"), List.of("sca"));

    /** Palabras sueltas que no aportan al final del código. */
    private static final Set<String> CONECTORES = Set.of("y", "de", "del", "la", "las", "los", "el");

    /** Clase utilitaria: no se instancia. */
    private GeneradorCodigoEmpresa() {
    }

    /**
     * Código base a partir de la razón social.
     *
     * @param razonSocial nombre legal
     * @return código en minúsculas, con guiones, entre {@value #LARGO_MINIMO} y {@value #LARGO_MAXIMO} caracteres
     */
    public static String base(String razonSocial) {
        String limpio = Normalizer.normalize(razonSocial == null ? "" : razonSocial, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("&", " y ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        List<String> palabras = new ArrayList<>(limpio.isEmpty() ? List.of() : Arrays.asList(limpio.split(" ")));
        quitarSiglas(palabras);
        List<String> elegidas = new ArrayList<>();
        int largo = 0;
        for (String p : palabras) {
            int conEsta = largo + (elegidas.isEmpty() ? 0 : 1) + p.length();
            if (conEsta > LARGO_MAXIMO) {
                break;
            }
            elegidas.add(p);
            largo = conEsta;
        }
        // Al cortar puede quedar un conector suelto al final ("distribuidora-nacional-de"): se quita.
        while (elegidas.size() > 1 && CONECTORES.contains(elegidas.get(elegidas.size() - 1))) {
            elegidas.remove(elegidas.size() - 1);
        }
        String codigo = String.join("-", elegidas);
        if (codigo.isEmpty() && !palabras.isEmpty()) {
            // Una sola palabra más larga que el máximo: se corta.
            codigo = palabras.get(0).substring(0, Math.min(LARGO_MAXIMO, palabras.get(0).length()));
        }
        return codigo.length() < LARGO_MINIMO ? RESPALDO : codigo;
    }

    /**
     * Código con un número al final para cuando el base ya existe ({@code ferreteria-el-tornillo-2}).
     *
     * @param base   código base
     * @param numero número a partir de 2
     * @return código de máximo {@value #LARGO_MAXIMO} caracteres que termina en {@code -numero}
     */
    public static String conNumero(String base, int numero) {
        String sufijo = "-" + numero;
        String recortado = base.length() + sufijo.length() > LARGO_MAXIMO
                ? base.substring(0, LARGO_MAXIMO - sufijo.length()).replaceAll("-+$", "")
                : base;
        return recortado + sufijo;
    }

    /**
     * Quita del final las siglas societarias y los conectores que queden sueltos.
     *
     * @param palabras palabras de la razón social, en orden
     */
    private static void quitarSiglas(List<String> palabras) {
        boolean cambio = true;
        while (cambio && !palabras.isEmpty()) {
            cambio = false;
            for (List<String> sigla : SIGLAS) {
                int n = sigla.size();
                // Si la razón social es solo la sigla, queda vacía y se usa el código de respaldo.
                if (palabras.size() >= n && palabras.subList(palabras.size() - n, palabras.size()).equals(sigla)) {
                    palabras.subList(palabras.size() - n, palabras.size()).clear();
                    cambio = true;
                    break;
                }
            }
            if (!cambio && palabras.size() > 1 && CONECTORES.contains(palabras.get(palabras.size() - 1))) {
                palabras.remove(palabras.size() - 1);
                cambio = true;
            }
        }
    }
}
