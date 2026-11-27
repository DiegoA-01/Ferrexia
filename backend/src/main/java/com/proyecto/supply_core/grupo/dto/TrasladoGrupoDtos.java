package com.proyecto.supply_core.grupo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.grupo.enums.EstadoTrasladoGrupo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Datos de entrada y de salida de los traslados entre empresas del grupo (spec 035). */
public final class TrasladoGrupoDtos {

    /** Máximo de líneas de un traslado. */
    public static final int MAX_LINEAS = 200;

    /** Clase de contenedores: no se instancia. */
    private TrasladoGrupoDtos() {
    }

    /**
     * Empresa del mismo grupo a la que se puede enviar mercancía: solo su id y su nombre, sin datos de negocio.
     *
     * @param id     empresa
     * @param nombre razón social
     */
    public record DestinoResponse(Long id, String nombre) {
    }

    /**
     * Línea de un envío.
     *
     * @param productoId producto de la empresa origen
     * @param cantidad   cantidad a trasladar (> 0)
     */
    public record LineaEnvioRequest(
            @NotNull(message = "es obligatorio")
            @Positive(message = "debe ser un id válido")
            Long productoId,

            @NotNull(message = "es obligatoria")
            @DecimalMin(value = "0.0001", message = "debe ser mayor que cero")
            BigDecimal cantidad) {
    }

    /**
     * Envío de mercancía a otra empresa del grupo.
     *
     * @param empresaDestinoId empresa del mismo grupo que recibe
     * @param bodegaOrigenId   bodega de la empresa origen de la que sale
     * @param lineas           productos y cantidades
     * @param nota             nota opcional
     */
    public record EnviarTrasladoRequest(
            @NotNull(message = "es obligatoria")
            @Positive(message = "debe ser un id válido")
            Long empresaDestinoId,

            @NotNull(message = "es obligatoria")
            @Positive(message = "debe ser un id válido")
            Long bodegaOrigenId,

            @NotEmpty(message = "debe tener al menos una línea")
            @Size(max = MAX_LINEAS, message = "máximo 200 líneas")
            List<@Valid @NotNull(message = "no puede ser nula") LineaEnvioRequest> lineas,

            @Size(max = 300, message = "máximo 300 caracteres")
            String nota) {
    }

    /**
     * Lo recibido de una línea.
     *
     * @param lineaId          línea del traslado
     * @param cantidadRecibida lo que llegó (de 0 a lo enviado)
     */
    public record LineaRecepcionTrasladoRequest(
            @NotNull(message = "es obligatorio")
            @Positive(message = "debe ser un id válido")
            Long lineaId,

            @NotNull(message = "es obligatoria")
            @PositiveOrZero(message = "no puede ser negativa")
            BigDecimal cantidadRecibida) {
    }

    /**
     * Recepción de un traslado en la empresa destino.
     *
     * @param bodegaDestinoId bodega de la empresa destino a la que entra
     * @param lineas          lo recibido de cada línea (todas)
     * @param nota            nota opcional de la recepción
     */
    public record RecibirTrasladoRequest(
            @NotNull(message = "es obligatoria")
            @Positive(message = "debe ser un id válido")
            Long bodegaDestinoId,

            @NotEmpty(message = "debe indicar lo recibido de cada línea")
            @Size(max = MAX_LINEAS, message = "máximo 200 líneas")
            List<@Valid @NotNull(message = "no puede ser nula") LineaRecepcionTrasladoRequest> lineas,

            @Size(max = 300, message = "máximo 300 caracteres")
            String nota) {
    }

    /**
     * Anulación de un traslado en tránsito.
     *
     * @param motivo motivo obligatorio
     */
    public record AnularTrasladoRequest(
            @NotBlank(message = "es obligatorio")
            @Size(max = 300, message = "máximo 300 caracteres")
            String motivo) {
    }

    /**
     * Línea de un traslado vista desde la empresa del contexto: el producto es el de ESA empresa (la otra no lo ve).
     *
     * @param id               línea
     * @param productoId       producto de la empresa que consulta
     * @param codigo           código del producto
     * @param nombre           nombre del producto
     * @param cantidad         enviada
     * @param cantidadRecibida recibida ({@code null} mientras no se reciba)
     * @param faltante         enviada menos recibida ({@code null} mientras no se reciba)
     * @param costoUnitario    costo unitario con que salió de la origen
     */
    public record LineaTrasladoResponse(Long id, Long productoId, String codigo, String nombre, BigDecimal cantidad,
            BigDecimal cantidadRecibida, BigDecimal faltante, BigDecimal costoUnitario) {
    }

    /**
     * Traslado en una lista.
     *
     * @param id                  traslado
     * @param consecutivo         consecutivo TG de la origen
     * @param estado              estado
     * @param sentido             {@code ENVIADO} si la empresa que consulta es la origen, {@code RECIBIDO} si es la destino
     * @param empresaOrigenId     empresa origen
     * @param empresaOrigenNombre razón social de la origen
     * @param empresaDestinoId    empresa destino
     * @param empresaDestinoNombre razón social de la destino
     * @param lineas              cantidad de líneas
     * @param nota                nota del envío
     * @param enviadoEn           cuándo se envió
     * @param recibidoEn          cuándo se recibió
     * @param anuladoEn           cuándo se anuló
     */
    public record TrasladoResumenResponse(Long id, String consecutivo, EstadoTrasladoGrupo estado, String sentido,
            Long empresaOrigenId, String empresaOrigenNombre, Long empresaDestinoId, String empresaDestinoNombre,
            int lineas, String nota, LocalDateTime enviadoEn, LocalDateTime recibidoEn, LocalDateTime anuladoEn) {
    }

    /**
     * Traslado con sus líneas.
     *
     * @param resumen         datos de la lista
     * @param bodegaPropiaId  bodega de la empresa que consulta (la de salida si es la origen; la de entrada si es la
     *                        destino y ya lo recibió)
     * @param notaRecepcion   nota de la recepción
     * @param motivoAnulacion motivo de la anulación
     * @param lineas          líneas vistas desde la empresa que consulta
     */
    public record TrasladoDetalleResponse(TrasladoResumenResponse resumen, Long bodegaPropiaId, String notaRecepcion,
            String motivoAnulacion, List<LineaTrasladoResponse> lineas) {
    }
}
