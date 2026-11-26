package com.proyecto.supply_core.domicilio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.domicilio.entity.Domicilio;
import com.proyecto.supply_core.venta.dto.PagoVentaRequest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Peticiones y respuestas de domicilios (spec 032). */
public final class DomicilioDtos {

    /** Solo agrupa los records. */
    private DomicilioDtos() {
    }

    /**
     * Un domicilio con su venta y, en el detalle, su historial.
     *
     * @param id                   domicilio
     * @param consecutivo          consecutivo DOM
     * @param ventaId              venta
     * @param venta                consecutivo de la venta
     * @param cliente              nombre del cliente
     * @param total                total de la venta
     * @param direccion            dirección de entrega
     * @param telefono             teléfono de quien recibe
     * @param referencia           indicaciones
     * @param pago                 PAGADO o CONTRAENTREGA
     * @param estado               estado
     * @param domiciliarioUsuarioId domiciliario interno, o {@code null}
     * @param domiciliario         nombre del domiciliario (interno o externo)
     * @param domiciliarioTelefono teléfono del domiciliario externo
     * @param motivo               motivo de la devolución o cancelación
     * @param creadoEn             cuándo se vendió
     * @param entregadoEn          cuándo se entregó
     * @param cobradoEn            cuándo se cobró la contraentrega
     * @param porCobrar            {@code true} si es contraentrega entregada y sin cobrar
     * @param eventos              historial (solo en el detalle)
     */
    @Schema(name = "DomicilioResponse")
    public record Respuesta(Long id, String consecutivo, Long ventaId, String venta, String cliente, BigDecimal total,
            String direccion, String telefono, String referencia, Domicilio.Pago pago, Domicilio.Estado estado,
            Long domiciliarioUsuarioId, String domiciliario, String domiciliarioTelefono, String motivo,
            LocalDateTime creadoEn, LocalDateTime entregadoEn, LocalDateTime cobradoEn, boolean porCobrar,
            List<Evento> eventos) {
    }

    /**
     * Un cambio del domicilio.
     *
     * @param estado  estado que quedó (o COBRADO)
     * @param usuario quien lo hizo
     * @param nota    nota
     * @param fecha   cuándo
     */
    @Schema(name = "DomicilioEvento")
    public record Evento(String estado, String usuario, String nota, LocalDateTime fecha) {
    }

    /**
     * Asignar el domiciliario: interno (usuario con el permiso de repartir) o externo (nombre y teléfono).
     *
     * @param domiciliarioUsuarioId usuario interno, o {@code null}
     * @param nombre                nombre del externo
     * @param telefono              teléfono del externo
     */
    @Schema(name = "AsignarDomicilioRequest")
    public record Asignar(@Positive(message = "debe ser un id válido") Long domiciliarioUsuarioId,
            @Size(max = 150, message = "admite hasta 150 caracteres") String nombre,
            @Size(max = 30, message = "admite hasta 30 caracteres") String telefono) {
    }

    /**
     * Cambio de estado.
     *
     * @param estado EN_CAMINO, ENTREGADO, DEVUELTO o CANCELADO
     * @param nota   nota (obligatoria para devolver o cancelar)
     */
    @Schema(name = "CambioEstadoDomicilioRequest")
    public record CambioEstado(@NotNull(message = "es obligatorio") Domicilio.Estado estado,
            @Size(max = 300, message = "admite hasta 300 caracteres") String nota) {
    }

    /**
     * Cobro de la contraentrega en la caja.
     *
     * @param pagos pagos por medios de caja que suman el total de la venta
     */
    @Schema(name = "CobroDomicilioRequest")
    public record Cobro(@NotEmpty(message = "agregue al menos un pago") @Size(max = 5, message = "admite hasta 5 pagos")
            List<@Valid PagoVentaRequest> pagos) {
    }

    /**
     * Producto del envío a domicilio.
     *
     * @param productoId producto servicio, o {@code null} si la empresa no lo ha configurado
     * @param codigo     código del producto
     * @param nombre     nombre del producto
     * @param precio     precio vigente en la lista General, o {@code null}
     */
    @Schema(name = "EnvioDomicilioResponse")
    public record Envio(Long productoId, String codigo, String nombre, BigDecimal precio) {
    }

    /**
     * Precio del envío a domicilio.
     *
     * @param precio precio en la lista General
     */
    @Schema(name = "EnvioDomicilioRequest")
    public record EnvioPrecio(@NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que cero")
            @Digits(integer = 16, fraction = 2, message = "admite hasta 2 decimales") BigDecimal precio) {
    }

    /**
     * Un domiciliario interno.
     *
     * @param id     usuario
     * @param nombre nombre completo
     */
    @Schema(name = "DomiciliarioResponse")
    public record Domiciliario(Long id, String nombre) {
    }
}
