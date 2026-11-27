package com.proyecto.supply_core.grupo.enums;

/** Estado de un traslado entre empresas del grupo (ENUM {@code traslado_grupo.estado}). */
public enum EstadoTrasladoGrupo {
    /** Salió de la empresa origen y espera que la destino lo reciba. */
    EN_TRANSITO,
    /** La empresa destino lo recibió (con o sin faltante). */
    RECIBIDO,
    /** La empresa origen lo anuló antes de que lo recibieran y recuperó el stock. */
    ANULADO
}
