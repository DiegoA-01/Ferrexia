package com.proyecto.supply_core.usuario.enums;

/** Con qué entra una persona (spec 022, decisión D10). En los dos casos solo se guarda el hash BCrypt. */
public enum TipoCredencial {
    /** Contraseña: fuerte para dueño y administradores; la de siempre para proveedores y claves anteriores. */
    CLAVE,
    /** PIN de 6 dígitos que asigna el jefe al personal; el trabajador no lo puede cambiar. */
    PIN
}
