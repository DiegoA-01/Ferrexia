package com.proyecto.supply_core.common.exception;

/**
 * La función existía y se retiró a propósito (por ejemplo, el portal del proveedor en la spec 031). Se responde con
 * HTTP 410.
 */
public class RecursoRetiradoException extends RuntimeException {

    private final String codigo;

    /**
     * Crea la excepción.
     *
     * @param codigo  código propio (por ejemplo {@code PORTAL_RETIRADO})
     * @param mensaje texto legible para el usuario
     */
    public RecursoRetiradoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    /**
     * Código propio del retiro.
     *
     * @return código
     */
    public String getCodigo() {
        return codigo;
    }
}
