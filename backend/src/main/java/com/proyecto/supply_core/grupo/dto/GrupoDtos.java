package com.proyecto.supply_core.grupo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.proyecto.supply_core.grupo.entity.GrupoEmpresa;
import com.proyecto.supply_core.grupo.entity.UsuarioGrupo;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.usuario.validation.ReglasPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos de entrada y de salida de los grupos de empresas (consola de la plataforma y zona del grupo). */
public final class GrupoDtos {

    /** Largo mínimo de la clave de un super administrador (más exigente que la de un usuario común). */
    public static final int CLAVE_MINIMA = 12;

    /** Clase de contenedores: no se instancia. */
    private GrupoDtos() {
    }

    /**
     * Alta de un grupo.
     *
     * @param nombre nombre del grupo
     */
    public record GrupoCrearRequest(
            @NotBlank(message = "es obligatorio")
            @Size(max = 150, message = "máximo 150 caracteres")
            String nombre) {
    }

    /**
     * Asignar una empresa a un grupo o sacarla de él.
     *
     * @param grupoId grupo al que va la empresa; {@code null} la saca de su grupo
     */
    public record AsignarGrupoRequest(Long grupoId) {
    }

    /**
     * Alta del super administrador de un grupo.
     *
     * @param nombre   nombre completo
     * @param correo   correo de login (único en toda la plataforma)
     * @param password clave fuerte de 12 o más caracteres con letras y números
     */
    public record SuperAdminCrearRequest(
            @NotBlank(message = "es obligatorio")
            @Size(max = 150, message = "máximo 150 caracteres")
            String nombre,

            @NotBlank(message = "es obligatorio")
            @Email(message = "no es un correo válido")
            @Size(max = 150, message = "máximo 150 caracteres")
            String correo,

            @NotBlank(message = "es obligatoria")
            @Size(min = CLAVE_MINIMA, max = ReglasPassword.MAXIMO, message = "debe tener entre 12 y 72 caracteres")
            @Pattern(regexp = ReglasPassword.PATRON, message = ReglasPassword.MENSAJE_PATRON)
            String password) {
    }

    /**
     * Activar o desactivar a un super administrador.
     *
     * @param activo {@code true} lo activa, {@code false} lo desactiva y corta sus sesiones
     */
    public record SuperAdminEstadoRequest(@NotNull(message = "es obligatorio") Boolean activo) {
    }

    /**
     * Resultado de asignar una empresa a un grupo.
     *
     * @param empresaId empresa
     * @param grupoId   grupo actual de la empresa ({@code null} si quedó sin grupo)
     */
    public record AsignacionGrupoResponse(Long empresaId, Long grupoId) {
    }

    /**
     * Empresa dentro de un grupo.
     *
     * @param id            id de la empresa
     * @param razonSocial   nombre de la empresa
     * @param codigoPublico código con el que se entra a la empresa
     */
    public record EmpresaDeGrupoResponse(Long id, String razonSocial, String codigoPublico) {

        /**
         * @param e empresa
         * @return su vista dentro del grupo
         */
        public static EmpresaDeGrupoResponse of(Empresa e) {
            return new EmpresaDeGrupoResponse(e.getId(), e.getRazonSocial(), e.getCodigoPublico());
        }
    }

    /**
     * Super administrador de un grupo (sin hash ni secretos).
     *
     * @param id        id
     * @param nombre    nombre
     * @param correo    correo de login
     * @param activo    si puede entrar
     * @param mfaActivo si ya vinculó su app de autenticación
     */
    public record SuperAdminResponse(Long id, String nombre, String correo, boolean activo, boolean mfaActivo) {

        /**
         * @param u super administrador
         * @return su vista pública
         */
        public static SuperAdminResponse of(UsuarioGrupo u) {
            return new SuperAdminResponse(u.getId(), u.getNombre(), u.getCorreo(), u.isActivo(), u.isMfaActivo());
        }
    }

    /**
     * Grupo con sus empresas y sus super administradores.
     *
     * @param id          id del grupo
     * @param nombre      nombre
     * @param activo      si el grupo está activo
     * @param empresas    empresas del grupo
     * @param superAdmins super administradores del grupo
     */
    public record GrupoResponse(Long id, String nombre, boolean activo, List<EmpresaDeGrupoResponse> empresas,
            List<SuperAdminResponse> superAdmins) {

        /**
         * @param g           grupo
         * @param empresas    sus empresas
         * @param superAdmins sus super administradores
         * @return respuesta armada
         */
        public static GrupoResponse of(GrupoEmpresa g, List<Empresa> empresas, List<UsuarioGrupo> superAdmins) {
            return new GrupoResponse(g.getId(), g.getNombre(), g.isActivo(),
                    empresas.stream().map(EmpresaDeGrupoResponse::of).toList(),
                    superAdmins.stream().map(SuperAdminResponse::of).toList());
        }
    }

    /**
     * Primer paso del login del grupo.
     *
     * @param correo   correo del super administrador
     * @param password contraseña
     */
    public record LoginGrupoRequest(
            @NotBlank(message = "es obligatorio")
            @Size(max = 150, message = "máximo 150 caracteres")
            String correo,

            @NotBlank(message = "es obligatoria")
            @Size(max = 72, message = "máximo 72 caracteres")
            String password) {
    }

    /**
     * Segundo paso del login del grupo: el código de la app de autenticación.
     *
     * @param desafio desafío entregado al acertar la contraseña
     * @param codigo  código de 6 dígitos
     */
    public record MfaGrupoRequest(
            @NotBlank(message = "es obligatorio")
            String desafio,

            @NotBlank(message = "es obligatorio")
            @Pattern(regexp = "^\\d{6}$", message = "debe tener 6 dígitos")
            String codigo) {
    }

    /**
     * Sesión del grupo tras acertar la contraseña y el código de MFA.
     *
     * @param token            JWT; solo sirve en {@code /api/grupo/**}
     * @param tipo             siempre {@code Bearer}
     * @param expiraEnSegundos segundos de vigencia
     * @param usuarioId        id del super administrador
     * @param correo           correo
     * @param nombre           nombre
     * @param grupoId          grupo
     * @param grupoNombre      nombre del grupo
     */
    public record SesionGrupoResponse(String token, String tipo, long expiraEnSegundos, Long usuarioId, String correo,
            String nombre, Long grupoId, String grupoNombre) {
    }

    /**
     * Empresa del grupo a la que el super administrador puede entrar.
     *
     * @param id            id de la empresa
     * @param razonSocial   nombre
     * @param codigoPublico código de la empresa
     * @param estado        estado de su suscripción (una suspendida no deja entrar)
     */
    public record EmpresaEntrableResponse(Long id, String razonSocial, String codigoPublico, String estado) {
    }

    /**
     * Cifras de una empresa en el tablero del grupo.
     *
     * @param empresaId            empresa
     * @param razonSocial          nombre
     * @param ventasNetas          ventas netas del periodo (ventas menos devoluciones)
     * @param compras              compras del periodo (órdenes vigentes, con impuestos)
     * @param inventarioValorizado stock actual por costo de referencia
     */
    public record TableroEmpresaResponse(Long empresaId, String razonSocial, BigDecimal ventasNetas, BigDecimal compras,
            BigDecimal inventarioValorizado) {
    }

    /**
     * Tablero del grupo: lo de cada empresa y el total.
     *
     * @param desde    primer día del periodo
     * @param hasta    último día del periodo
     * @param empresas cifras por empresa
     * @param total    suma de todas las empresas ({@code empresaId} y {@code razonSocial} vacíos)
     */
    public record TableroGrupoResponse(LocalDate desde, LocalDate hasta, List<TableroEmpresaResponse> empresas,
            TableroEmpresaResponse total) {
    }
}
