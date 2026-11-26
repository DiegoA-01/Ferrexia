package com.proyecto.supply_core.proveedor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta interna y corta de un proveedor (spec 026), por ejemplo desde el formulario del producto. Solo el nombre es
 * obligatorio; queda habilitado y sin usuario del portal.
 *
 * @param razonSocial nombre del proveedor
 * @param nit         NIT opcional (solo números, con dígito de verificación opcional); único en la empresa
 * @param correo      correo de contacto opcional
 * @param telefono    teléfono opcional
 */
public record ProveedorNuevoRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 200, message = "máximo 200 caracteres") String razonSocial,
        @Pattern(regexp = "^$|^\\d{6,15}(-\\d)?$", message = "solo números, con dígito de verificación opcional")
        String nit,
        @Email(message = "no es un correo válido") @Size(max = 150, message = "máximo 150 caracteres") String correo,
        @Size(max = 30, message = "máximo 30 caracteres") String telefono) {
}
