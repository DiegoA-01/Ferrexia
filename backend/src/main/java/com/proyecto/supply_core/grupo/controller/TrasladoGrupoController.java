package com.proyecto.supply_core.grupo.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.AnularTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.DestinoResponse;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.EnviarTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.RecibirTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.TrasladoDetalleResponse;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.TrasladoResumenResponse;
import com.proyecto.supply_core.grupo.service.TrasladoGrupoService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequierePermiso;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Permisos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Traslados de mercancía entre empresas del mismo grupo (spec 035). Va con el token de la EMPRESA (no el del grupo):
 * enviar y anular los hace la empresa origen; recibir, la destino. Quien consulta ve los de su empresa.
 */
@RequiereModulo(Modulos.INVENTARIO)
@RestController
@RequestMapping("/api/traslados-grupo")
@UsuarioInterno
public class TrasladoGrupoController {

    private final TrasladoGrupoService service;

    /**
     * @param service lógica de los traslados entre empresas
     */
    public TrasladoGrupoController(TrasladoGrupoService service) {
        this.service = service;
    }

    /**
     * Empresas del mismo grupo a las que se puede enviar (solo id y nombre).
     *
     * @return destinos posibles
     */
    @GetMapping("/destinos")
    @RequierePermiso(Permisos.TRASLADO_GRUPO_ENVIAR)
    public List<DestinoResponse> destinos() {
        return service.destinos();
    }

    /**
     * Envía mercancía a otra empresa del grupo.
     *
     * @param request destino, bodega de salida, líneas y nota
     * @return traslado creado EN_TRANSITO (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequierePermiso(Permisos.TRASLADO_GRUPO_ENVIAR)
    public TrasladoDetalleResponse enviar(@Valid @RequestBody EnviarTrasladoRequest request) {
        return service.enviar(request);
    }

    /**
     * Recibe un traslado en la empresa destino.
     *
     * @param id      id positivo del traslado
     * @param request bodega destino, lo recibido de cada línea y nota
     * @return traslado recibido
     */
    @PostMapping("/{id}/recibir")
    @RequierePermiso(Permisos.TRASLADO_GRUPO_RECIBIR)
    public TrasladoDetalleResponse recibir(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody RecibirTrasladoRequest request) {
        return service.recibir(id, request);
    }

    /**
     * Anula un traslado en tránsito desde la empresa origen.
     *
     * @param id      id positivo del traslado
     * @param request motivo
     * @return traslado anulado
     */
    @PostMapping("/{id}/anular")
    @RequierePermiso(Permisos.TRASLADO_GRUPO_ENVIAR)
    public TrasladoDetalleResponse anular(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody AnularTrasladoRequest request) {
        return service.anular(id, request);
    }

    /**
     * Traslados enviados y recibidos por la empresa, los más nuevos primero.
     *
     * Exige al menos uno de los dos permisos de traslados (lo valida el servicio: la anotación admite uno solo).
     *
     * @param pageable página (máximo 100)
     * @return página de traslados
     */
    @GetMapping
    public PageResponse<TrasladoResumenResponse> listar(@PageableDefault(size = 20) Pageable pageable) {
        return service.listar(pageable);
    }

    /**
     * Un traslado con sus líneas. Exige al menos uno de los dos permisos de traslados (lo valida el servicio).
     *
     * @param id id positivo del traslado
     * @return traslado con líneas
     */
    @GetMapping("/{id}")
    public TrasladoDetalleResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return service.obtener(id);
    }
}
