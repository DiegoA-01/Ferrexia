package com.proyecto.supply_core.usuario.validation;

import com.proyecto.supply_core.usuario.service.CorreoGlobal;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link CorreoUnico}. Spring lo crea e inyecta el repositorio por constructor. */
public class CorreoUnicoValidator implements ConstraintValidator<CorreoUnico, String> {

    private final CorreoGlobal correoGlobal;

    /**
     * @param correoGlobal consulta del correo en todas las empresas
     */
    public CorreoUnicoValidator(CorreoGlobal correoGlobal) {
        this.correoGlobal = correoGlobal;
    }

    /**
     * Comprueba que ningún usuario de ninguna empresa tenga ya el correo (el interno de un super administrador no cuenta).
     *
     * @param correo  valor recibido
     * @param context contexto de validación
     * @return {@code true} si está libre o vacío (el vacío lo reporta {@code @NotBlank})
     */
    @Override
    public boolean isValid(String correo, ConstraintValidatorContext context) {
        if (correo == null || correo.isBlank()) {
            return true;
        }
        return !correoGlobal.enUso(correo, null);
    }
}
