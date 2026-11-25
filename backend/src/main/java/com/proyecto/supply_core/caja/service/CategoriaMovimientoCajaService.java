package com.proyecto.supply_core.caja.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.dto.CategoriaMovimientoCajaCambio;
import com.proyecto.supply_core.caja.dto.CategoriaMovimientoCajaRequest;
import com.proyecto.supply_core.caja.dto.CategoriaMovimientoCajaResponse;
import com.proyecto.supply_core.caja.entity.CategoriaMovimientoCaja;
import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;
import com.proyecto.supply_core.caja.repository.CategoriaMovimientoCajaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;

/**
 * Categorías de ingresos y egresos de caja (spec 021). Cada empresa tiene las suyas; vienen algunas por defecto. No se
 * borran (los movimientos ya registrados las nombran): se desactivan y dejan de ofrecerse.
 */
@Service
public class CategoriaMovimientoCajaService {

    /** Las que recibe cada empresa nueva (las mismas que V42 sembró en las que ya existían). */
    static final List<CategoriaMovimientoCajaRequest> POR_DEFECTO = List.of(
            new CategoriaMovimientoCajaRequest(TipoCategoriaMovimiento.EGRESO, "Papelería"),
            new CategoriaMovimientoCajaRequest(TipoCategoriaMovimiento.EGRESO, "Aseo"),
            new CategoriaMovimientoCajaRequest(TipoCategoriaMovimiento.EGRESO, "Domicilio"),
            new CategoriaMovimientoCajaRequest(TipoCategoriaMovimiento.EGRESO, "Otro"),
            new CategoriaMovimientoCajaRequest(TipoCategoriaMovimiento.INGRESO, "Base adicional"),
            new CategoriaMovimientoCajaRequest(TipoCategoriaMovimiento.INGRESO, "Otro"));

    private final CategoriaMovimientoCajaRepository repository;
    private final AuditoriaService auditoriaService;

    /**
     * @param repository       categorías
     * @param auditoriaService registro de cambios
     */
    public CategoriaMovimientoCajaService(CategoriaMovimientoCajaRepository repository,
            AuditoriaService auditoriaService) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * @param tipo   filtro por tipo ({@code null} = los dos)
     * @param activa filtro por estado ({@code null} = todas)
     * @return categorías de la empresa por tipo y nombre
     */
    @Transactional(readOnly = true)
    public List<CategoriaMovimientoCajaResponse> listar(TipoCategoriaMovimiento tipo, Boolean activa) {
        return repository.listar(tipo, activa).stream().map(CategoriaMovimientoCajaResponse::of).toList();
    }

    /**
     * @param req tipo y nombre
     * @return categoría creada
     * @throws NegocioException si ya hay una del mismo tipo con ese nombre
     */
    @Transactional
    public CategoriaMovimientoCajaResponse crear(CategoriaMovimientoCajaRequest req) {
        String nombre = limpiar(req.nombre());
        validarNombre(req.tipo(), nombre, -1L);
        CategoriaMovimientoCaja c = new CategoriaMovimientoCaja();
        c.setTipo(req.tipo());
        c.setNombre(nombre);
        repository.save(c);
        auditoriaService.exito(AccionAuditoria.CATEGORIA_CAJA_CREAR, EntidadAuditada.CATEGORIA_MOVIMIENTO_CAJA,
                c.getId(), c.getTipo() + " " + nombre);
        return CategoriaMovimientoCajaResponse.of(c);
    }

    /**
     * Renombra, desactiva o reactiva una categoría. Desactivar una que ya se usó solo la oculta: los movimientos
     * registrados la siguen mostrando.
     *
     * @param id  categoría
     * @param req nombre y estado
     * @return categoría actualizada
     * @throws NoEncontradoException si no es de la empresa
     * @throws NegocioException      si el nombre nuevo ya existe en el mismo tipo
     */
    @Transactional
    public CategoriaMovimientoCajaResponse editar(Long id, CategoriaMovimientoCajaCambio req) {
        CategoriaMovimientoCaja c = repository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Categoría no encontrada"));
        String nombre = limpiar(req.nombre());
        validarNombre(c.getTipo(), nombre, id);
        String antes = c.getNombre() + (c.isActiva() ? "" : " (inactiva)");
        c.setNombre(nombre);
        c.setActiva(req.activa());
        auditoriaService.exito(AccionAuditoria.CATEGORIA_CAJA_EDITAR, EntidadAuditada.CATEGORIA_MOVIMIENTO_CAJA, id,
                "antes: " + antes + " | después: " + nombre + (c.isActiva() ? "" : " (inactiva)"));
        return CategoriaMovimientoCajaResponse.of(c);
    }

    /**
     * La categoría que se eligió para un movimiento: debe ser de la empresa, estar activa y ser del tipo que toca.
     *
     * @param id   categoría
     * @param tipo tipo que exige el movimiento
     * @return categoría
     * @throws NegocioException {@code CATEGORIA_INVALIDA} si no existe para la empresa, está inactiva o es del otro tipo
     */
    @Transactional(readOnly = true)
    public CategoriaMovimientoCaja paraMovimiento(Long id, TipoCategoriaMovimiento tipo) {
        CategoriaMovimientoCaja c = repository.findById(id).orElse(null);
        if (c == null || !c.isActiva() || c.getTipo() != tipo) {
            throw new NegocioException("CATEGORIA_INVALIDA", "Elija una categoría de " + nombreTipo(tipo) + " activa");
        }
        return c;
    }

    /**
     * Crea las categorías por defecto si la empresa del contexto aún no tiene ninguna (alta de una empresa nueva).
     */
    @Transactional
    public void sembrarPorDefecto() {
        if (repository.hayAlguna()) {
            return;
        }
        for (CategoriaMovimientoCajaRequest d : POR_DEFECTO) {
            CategoriaMovimientoCaja c = new CategoriaMovimientoCaja();
            c.setTipo(d.tipo());
            c.setNombre(d.nombre());
            repository.save(c);
        }
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * @param nombre nombre escrito
     * @return sin espacios de más
     */
    private static String limpiar(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ");
    }

    /**
     * @param tipo tipo
     * @return "ingreso" o "egreso"
     */
    private static String nombreTipo(TipoCategoriaMovimiento tipo) {
        return tipo == TipoCategoriaMovimiento.INGRESO ? "ingreso" : "egreso";
    }

    /**
     * @param tipo   tipo
     * @param nombre nombre limpio
     * @param id     categoría que se edita (o -1 al crear)
     * @throws NegocioException si otra del mismo tipo ya usa el nombre
     */
    private void validarNombre(TipoCategoriaMovimiento tipo, String nombre, Long id) {
        if (repository.existsByTipoAndNombreIgnoreCaseAndIdNot(tipo, nombre, id)) {
            throw new NegocioException("CATEGORIA_DUPLICADA",
                    "Ya existe una categoría de " + nombreTipo(tipo) + " llamada " + nombre);
        }
    }
}
