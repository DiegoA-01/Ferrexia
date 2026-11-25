package com.proyecto.supply_core.usuario.validation;

import java.util.Set;
import java.util.regex.Pattern;

import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.enums.TipoCredencial;

/**
 * Reglas de la credencial según quién es la persona (spec 022, decisión D10):
 * <ul>
 *   <li>Quien administra usuarios (dueño, administrador o un rol propio con ese permiso): clave fuerte de 12 a 72
 *   caracteres con mayúscula, minúscula, número y símbolo.</li>
 *   <li>Proveedor: la clave de siempre (8 a 72, con letra y número).</li>
 *   <li>Personal: PIN de 6 dígitos que asigna el jefe, o la clave de siempre.</li>
 * </ul>
 * Las claves que ya existían siguen sirviendo: estas reglas se aplican al crear, restablecer o cambiar una.
 */
public final class ReglasCredencial {

    private static final Pattern PIN = Pattern.compile("^\\d{6}$");
    private static final Pattern CLASICA = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");
    private static final Pattern FUERTE = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,72}$");
    /** Mensaje de la clave fuerte. */
    public static final String MENSAJE_FUERTE = "Quien administra usuarios necesita una clave de 12 a 72 caracteres con "
            + "mayúscula, minúscula, número y símbolo";
    private static final String MENSAJE_PERSONAL = "Escriba un PIN de 6 dígitos o una clave de 8 a 72 caracteres con "
            + "letra y número";
    private static final String MENSAJE_CLASICA = "La clave debe tener de 8 a 72 caracteres, con letra y número";

    /** Clase de utilidades: no se instancia. */
    private ReglasCredencial() {
    }

    /**
     * @param roles     códigos de rol que tendrá la persona
     * @param usuarioId persona ({@code null} si aún no existe)
     * @param empresaId empresa
     * @return {@code true} si administra usuarios (le toca clave fuerte y MFA)
     */
    public static boolean administra(Set<String> roles, Long usuarioId, Long empresaId) {
        return new UsuarioAutenticado(usuarioId, "-", "-", roles, null, empresaId).tienePermiso(Permisos.USUARIO_GESTIONAR);
    }

    /**
     * Valida la credencial nueva y dice de qué tipo es.
     *
     * @param roles     códigos de rol que tendrá la persona
     * @param usuarioId persona ({@code null} si aún no existe)
     * @param empresaId empresa
     * @param valor     clave o PIN escritos
     * @return tipo de credencial
     * @throws ParametroInvalidoException si no cumple la regla que le toca
     */
    public static TipoCredencial validar(Set<String> roles, Long usuarioId, Long empresaId, String valor) {
        String v = valor == null ? "" : valor;
        if (roles.contains(Roles.PROVEEDOR)) {
            exigir(CLASICA, v, MENSAJE_CLASICA);
            return TipoCredencial.CLAVE;
        }
        if (administra(roles, usuarioId, empresaId)) {
            exigir(FUERTE, v, MENSAJE_FUERTE);
            return TipoCredencial.CLAVE;
        }
        if (PIN.matcher(v).matches()) {
            return TipoCredencial.PIN;
        }
        exigir(CLASICA, v, MENSAJE_PERSONAL);
        return TipoCredencial.CLAVE;
    }

    /**
     * @param valor clave escrita
     * @return {@code true} si cumple la regla fuerte
     */
    public static boolean esFuerte(String valor) {
        return valor != null && FUERTE.matcher(valor).matches();
    }

    /**
     * @param patron  regla
     * @param valor   texto
     * @param mensaje mensaje si no cumple
     */
    private static void exigir(Pattern patron, String valor, String mensaje) {
        if (!patron.matcher(valor).matches()) {
            throw new ParametroInvalidoException(mensaje);
        }
    }
}
