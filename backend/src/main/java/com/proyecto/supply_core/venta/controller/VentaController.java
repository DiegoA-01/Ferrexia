package com.proyecto.supply_core.venta.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequierePermiso;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.venta.dto.AnularVentaRequest;
import com.proyecto.supply_core.venta.dto.CotizacionVenta;
import com.proyecto.supply_core.venta.dto.CotizarRequest;
import com.proyecto.supply_core.venta.dto.LimiteDescuento;
import com.proyecto.supply_core.venta.dto.TiqueteResponse;
import com.proyecto.supply_core.venta.dto.VentaRequest;
import com.proyecto.supply_core.venta.dto.VentaResponse;
import com.proyecto.supply_core.venta.dto.VentaResumen;
import com.proyecto.supply_core.venta.enums.EstadoVenta;
import com.proyecto.supply_core.venta.service.AnulacionVentaService;
import com.proyecto.supply_core.venta.service.LimiteDescuentoService;
import com.proyecto.supply_core.venta.service.VentaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * La venta (spec 004): confirmarla en el turno, el historial y el límite de descuento por rol. Las rutas con id usan
 * {@code {id:\d+}} para no chocar con {@code /api/ventas/precios}, {@code /listas} y las demás de la spec 001.
 */
@RequiereModulo(Modulos.VENTAS)
@RestController
@RequestMapping("/api/ventas")
@UsuarioInterno
@Validated
public class VentaController {

    private final VentaService ventaService;
    private final LimiteDescuentoService limiteService;
    private final AccesoEmpresaService accesoEmpresa;
    private final AnulacionVentaService anulacionService;
    private final com.proyecto.supply_core.venta.service.CambioPagoVentaService cambioPagoService;

    /**
     * @param ventaService      lógica de la venta
     * @param limiteService     límites de descuento
     * @param accesoEmpresa     módulo de caja de la empresa
     * @param anulacionService  anulación de ventas (spec 006 y 029)
     * @param cambioPagoService cambio del método de pago (spec 029)
     */
    public VentaController(VentaService ventaService, LimiteDescuentoService limiteService,
            AccesoEmpresaService accesoEmpresa, AnulacionVentaService anulacionService,
            com.proyecto.supply_core.venta.service.CambioPagoVentaService cambioPagoService) {
        this.ventaService = ventaService;
        this.limiteService = limiteService;
        this.accesoEmpresa = accesoEmpresa;
        this.anulacionService = anulacionService;
        this.cambioPagoService = cambioPagoService;
    }

    /**
     * Confirma la venta. Si la misma clave ya se usó, devuelve la venta ya creada con 200 en vez de 201.
     *
     * @param clave   cabecera {@code Idempotency-Key} generada al abrir el carrito
     * @param request cliente, líneas, pagos y autorizaciones
     * @return la venta
     */
    @PostMapping
    @RequierePermiso(Permisos.VENTA_COBRAR)
    public ResponseEntity<VentaResponse> confirmar(@RequestHeader(name = "Idempotency-Key", required = false) String clave,
            @Valid @RequestBody VentaRequest request) {
        accesoEmpresa.exigirModulo(SesionActual.usuario().empresaId(), Modulos.CAJA);
        try {
            VentaService.Confirmacion c = ventaService.confirmar(request, clave);
            return ResponseEntity.status(c.nueva() ? HttpStatus.CREATED : HttpStatus.OK).body(c.venta());
        } catch (DataIntegrityViolationException e) {
            // Dos envíos con la misma clave al mismo tiempo: el segundo choca con la regla única y recibe la primera
            return ResponseEntity.ok(ventaService.porClave(clave));
        }
    }

    /**
     * @param cajaId      caja
     * @param turnoId     turno
     * @param clienteId   cliente
     * @param estado      estado
     * @param consecutivo consecutivo o su comienzo (quien no ve todas las ventas solo halla las de otro con el consecutivo completo)
     * @param desde       desde (incluido)
     * @param hasta       hasta (incluido)
     * @param pageable    página (por defecto las más recientes primero)
     * @return historial según quién mira
     */
    @GetMapping
    @RequierePermiso(Permisos.VENTA_VER)
    public PageResponse<VentaResumen> buscar(@RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long turnoId, @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) EstadoVenta estado, @RequestParam(required = false) String consecutivo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20, sort = "confirmadaEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return ventaService.buscar(cajaId, turnoId, clienteId, estado, consecutivo,
                desde == null ? null : desde.atStartOfDay(), hasta == null ? null : hasta.plusDays(1).atStartOfDay(),
                pageable);
    }

    /**
     * @param id venta
     * @return detalle con líneas y pagos
     */
    @GetMapping("/{id:\\d+}")
    @RequierePermiso(Permisos.VENTA_VER)
    public VentaResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return ventaService.obtener(id);
    }

    /**
     * Totales del carrito sin guardar nada (spec 005).
     *
     * @param request cliente, líneas y pagos escritos
     * @return totales, redondeo, cambio y autorizaciones que hacen falta
     */
    @PostMapping("/cotizar")
    @RequierePermiso(Permisos.VENTA_COTIZAR)
    public CotizacionVenta cotizar(@Valid @RequestBody CotizarRequest request) {
        return ventaService.cotizar(request);
    }

    /**
     * @param id venta
     * @return datos del tiquete (desde la segunda vez, COPIA)
     */
    @GetMapping("/{id:\\d+}/tiquete")
    @RequierePermiso(Permisos.VENTA_VER)
    public TiqueteResponse tiquete(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return ventaService.tiquete(id);
    }

    /**
     * Anula una venta del turno abierto (spec 006): devuelve el stock y saca el dinero de la caja.
     *
     * @param id      venta
     * @param request motivo y autorización del supervisor
     * @return la venta anulada
     */
    @PostMapping("/{id:\\d+}/anular")
    @RequierePermiso(Permisos.VENTA_COBRAR)
    public VentaResponse anular(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody AnularVentaRequest request) {
        accesoEmpresa.exigirModulo(SesionActual.usuario().empresaId(), Modulos.CAJA);
        return anulacionService.anular(id, request);
    }

    /**
     * Anula una venta de un turno ya cerrado (spec 029): el dinero sale del turno abierto de quien anula.
     *
     * @param id      venta
     * @param request motivo
     * @return la venta anulada
     */
    @PostMapping("/{id:\\d+}/anular-otro-turno")
    @RequierePermiso(Permisos.VENTA_ANULAR_OTRO_TURNO)
    public VentaResponse anularDeOtroTurno(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody com.proyecto.supply_core.venta.dto.AnularOtroTurnoRequest request) {
        accesoEmpresa.exigirModulo(SesionActual.usuario().empresaId(), Modulos.CAJA);
        return anulacionService.anularDeOtroTurno(id, request);
    }

    /**
     * Cambia el método de pago de una venta (spec 029, D16). Con el permiso no pide PIN; sin él, solo el cajero del
     * turno abierto de la venta y con la autorización del supervisor (lo decide el servicio).
     *
     * @param id      venta
     * @param request pagos nuevos, motivo y autorización opcional
     * @return la venta con sus pagos vigentes
     */
    @PostMapping("/{id:\\d+}/cambiar-pago")
    @RequierePermiso(Permisos.VENTA_VER)
    public VentaResponse cambiarPago(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody com.proyecto.supply_core.venta.dto.CambiarPagoRequest request) {
        accesoEmpresa.exigirModulo(SesionActual.usuario().empresaId(), Modulos.CAJA);
        return cambioPagoService.cambiar(id, request);
    }

    /**
     * @return descuento máximo sin autorización de cada rol de venta
     */
    @GetMapping("/limites-descuento")
    @RequierePermiso(Permisos.VENTA_LIMITES_VER)
    public List<LimiteDescuento> limites() {
        return limiteService.listar();
    }

    /**
     * @param limites límites por rol
     * @return límites vigentes
     */
    @PutMapping("/limites-descuento")
    @RequierePermiso(Permisos.VENTA_LIMITES_GESTIONAR)
    public List<LimiteDescuento> guardarLimites(
            @RequestBody @NotEmpty(message = "envíe al menos un límite") @Size(max = 10, message = "admite hasta 10 límites")
            List<@Valid LimiteDescuento> limites) {
        return limiteService.guardar(limites);
    }
}
