package com.proyecto.supply_core.plataforma.enums;

/** Acciones que quedan en la auditoría global de la plataforma ({@code auditoria_plataforma.accion}). */
public enum AccionPlataforma {
    /** Contraseña verificada y desafío de MFA entregado (EXITO o FALLO). */
    LOGIN,
    /** Código de MFA verificado: sesión de plataforma emitida (EXITO o FALLO). */
    MFA,
    /** Primer código válido: el usuario dejó configurado su MFA. */
    MFA_CONFIGURAR,
    /** Alta de una empresa con su administrador inicial. */
    EMPRESA_CREAR,
    /** Cambio de plan de una empresa. */
    EMPRESA_CAMBIAR_PLAN,
    /** Excepción de módulo para una empresa. */
    EMPRESA_MODULO,
    /** Cambio de estado de la suscripción de una empresa. */
    EMPRESA_ESTADO,
    /** Alta de un plan. */
    PLAN_CREAR,
    /** Cambio de los módulos o del nombre de un plan. */
    PLAN_EDITAR,
    /** Alta de un grupo de empresas (spec 035). */
    GRUPO_CREAR,
    /** Una empresa entró a un grupo, cambió de grupo o salió de él. */
    GRUPO_ASIGNAR_EMPRESA,
    /** Alta del super administrador de un grupo. */
    GRUPO_SUPER_ADMIN_CREAR,
    /** Un super administrador de grupo se activó o se desactivó. */
    GRUPO_SUPER_ADMIN_ESTADO,
    /** Contraseña de un super administrador de grupo verificada (EXITO o FALLO). */
    GRUPO_LOGIN,
    /** Código de MFA de un super administrador de grupo verificado: sesión de grupo emitida (EXITO o FALLO). */
    GRUPO_MFA,
    /** La plataforma borró el segundo factor de un super administrador de grupo y cortó sus sesiones. */
    GRUPO_SUPER_ADMIN_REINICIAR_MFA
}
