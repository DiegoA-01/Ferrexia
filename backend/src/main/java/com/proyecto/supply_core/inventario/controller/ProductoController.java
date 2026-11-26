package com.proyecto.supply_core.inventario.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.inventario.dto.ProductoPlazoRequest;
import com.proyecto.supply_core.inventario.dto.ProductoRequest;
import com.proyecto.supply_core.inventario.dto.ProductoResponse;
import com.proyecto.supply_core.inventario.dto.ProductoUpdateRequest;
import com.proyecto.supply_core.inventario.dto.CambioVariantesRequest;
import com.proyecto.supply_core.inventario.dto.ProductoConVariantesRequest;
import com.proyecto.supply_core.inventario.dto.VariantesResponse;
import com.proyecto.supply_core.inventario.service.ProductoService;
import com.proyecto.supply_core.inventario.service.VarianteService;
import com.proyecto.supply_core.security.annotation.RequierePermiso;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** UC-02 Catálogo de productos: cualquier usuario interno consulta; ADMIN_COMPRAS administra. */
@RequiereModulo(Modulos.INVENTARIO)
@RestController
@RequestMapping("/api/productos")
@UsuarioInterno
public class ProductoController {

    private final ProductoService productoService;
    private final VarianteService varianteService;

    /**
     * @param productoService lógica de productos
     * @param varianteService productos con variantes (spec 028)
     */
    public ProductoController(ProductoService productoService, VarianteService varianteService) {
        this.productoService = productoService;
        this.varianteService = varianteService;
    }

    /**
     * Lista productos con su stock total.
     *
     * @param texto       coincidencia en código o nombre
     * @param categoriaId categoría
     * @param activo      estado
     * @param agrupadores {@code false} deja fuera los productos generales con variantes (selectores, spec 028)
     * @param pageable    página (máximo 100) y orden (codigo, nombre)
     * @return página de productos
     */
    @GetMapping
    public PageResponse<ProductoResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long categoriaId,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) Boolean agrupadores,
            @PageableDefault(size = 20, sort = "codigo", direction = Sort.Direction.ASC) Pageable pageable) {
        return productoService.buscar(texto, categoriaId, activo, agrupadores, pageable);
    }

    /**
     * Productos activos por debajo de su stock mínimo (para generar solicitudes de compra).
     *
     * @return productos con alerta
     */
    @GetMapping("/alertas-stock")
    public List<ProductoResponse> alertasStock() {
        return productoService.alertasStockMinimo();
    }

    /**
     * Productos activos por encima de su stock máximo (spec 026, D17: se permite, pero se avisa).
     *
     * @return productos sobre el máximo
     */
    @GetMapping("/sobre-maximo")
    public List<ProductoResponse> sobreMaximo() {
        return productoService.sobreStockMaximo();
    }

    /**
     * Detalle de un producto.
     *
     * @param id id positivo
     * @return producto con stock total
     */
    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return productoService.obtener(id);
    }

    /**
     * Crea un producto.
     *
     * @param request datos validados
     * @return producto creado (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequierePermiso(Permisos.PRODUCTO_GESTIONAR)
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest request) {
        return productoService.crear(request);
    }

    /**
     * Edita un producto.
     *
     * @param id      id positivo
     * @param request datos validados
     * @return producto actualizado
     */
    @PutMapping("/{id}")
    @RequierePermiso(Permisos.PRODUCTO_GESTIONAR)
    public ProductoResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody ProductoUpdateRequest request) {
        return productoService.actualizar(id, request);
    }

    /**
     * Activa un producto.
     *
     * @param id id positivo
     * @return producto activo
     */
    @PatchMapping("/{id}/activar")
    @RequierePermiso(Permisos.PRODUCTO_GESTIONAR)
    public ProductoResponse activar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return productoService.cambiarEstado(id, true);
    }

    /**
     * Desactiva un producto (no se usa en nuevas operaciones; conserva su historial).
     *
     * @param id id positivo
     * @return producto inactivo
     */
    @PatchMapping("/{id}/desactivar")
    @RequierePermiso(Permisos.PRODUCTO_GESTIONAR)
    public ProductoResponse desactivar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return productoService.cambiarEstado(id, false);
    }

    /**
     * Cambia los días hábiles que tarda en llegar el producto y su colchón de seguridad. Vacío = el de la empresa.
     *
     * @param id      id positivo
     * @param request plazo de entrega y días de seguridad
     * @return producto actualizado
     */
    @PatchMapping("/{id}/plazo")
    @RequierePermiso(Permisos.PRODUCTO_GESTIONAR)
    public ProductoResponse cambiarPlazo(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody ProductoPlazoRequest request) {
        return productoService.cambiarPlazo(id, request);
    }

    /**
     * Crea un producto general con una variante por cada combinación de valores (spec 028).
     *
     * @param request datos comunes, atributos y una fila por combinación
     * @return el producto general con sus variantes (HTTP 201)
     */
    @PostMapping("/con-variantes")
    @ResponseStatus(HttpStatus.CREATED)
    @RequierePermiso(Permisos.PRODUCTO_GESTIONAR)
    public VariantesResponse crearConVariantes(@Valid @RequestBody ProductoConVariantesRequest request) {
        return varianteService.crear(request);
    }

    /**
     * Variantes de un producto general con su stock (spec 028).
     *
     * @param id producto general
     * @return el producto general, sus atributos y sus variantes
     */
    @GetMapping("/{id}/variantes")
    public VariantesResponse variantes(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return varianteService.variantes(id);
    }

    /**
     * Agrega o quita valores de los atributos: crea las combinaciones nuevas y desactiva las que salen (spec 028).
     *
     * @param id      producto general
     * @param request atributos con sus valores nuevos y los datos de las combinaciones nuevas
     * @return el producto general con sus variantes
     */
    @PutMapping("/{id}/variantes")
    @RequierePermiso(Permisos.PRODUCTO_GESTIONAR)
    public VariantesResponse cambiarVariantes(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody CambioVariantesRequest request) {
        return varianteService.cambiarValores(id, request);
    }
}
