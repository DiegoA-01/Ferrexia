package com.proyecto.supply_core.grupo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.EnviarTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.LineaEnvioRequest;
import com.proyecto.supply_core.grupo.entity.TrasladoGrupo;
import com.proyecto.supply_core.grupo.entity.TrasladoGrupoLinea;
import com.proyecto.supply_core.grupo.repository.TrasladoGrupoRepository;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.security.context.SesionActual;

/**
 * Pasos transaccionales del envío de un traslado a otra empresa del grupo. Cada método abre su transacción en la empresa
 * del contexto, por eso están en un bean aparte: {@code TrasladoGrupoService.enviar} los llama uno por uno, el de
 * emparejar dentro de {@code EmpresaContexto.como(destino, ...)} (la sesión de BD toma la empresa al abrirse).
 */
@Service
public class TrasladoGrupoPasos {

    /** Escala de las cantidades y los costos ({@code DECIMAL(18,4)}). */
    private static final int ESCALA = 4;

    private final EmpresaRepository empresaRepository;
    private final ProductoRepository productoRepository;
    private final BodegaRepository bodegaRepository;
    private final TrasladoGrupoRepository trasladoRepository;
    private final MovimientoInventarioService movimientoService;
    private final ConsecutivoGenerator consecutivos;
    private final AuditoriaService auditoriaService;

    /**
     * Producto de la origen tal como hace falta para emparejarlo en la destino.
     *
     * @param productoId   producto de la origen
     * @param codigo       código
     * @param codigoBarras código de barras o {@code null}
     * @param cantidad     cantidad total pedida del producto (las líneas repetidas se suman)
     * @param costo        costo unitario vigente (con el que sale)
     */
    public record Referencia(Long productoId, String codigo, String codigoBarras, BigDecimal cantidad, BigDecimal costo) {
    }

    /**
     * @param empresaRepository  empresas (globales)
     * @param productoRepository productos de la empresa del contexto
     * @param bodegaRepository   bodegas de la empresa del contexto
     * @param trasladoRepository traslados
     * @param movimientoService  único punto que mueve el stock
     * @param consecutivos       consecutivo TG de la origen
     * @param auditoriaService   auditoría de la empresa que actúa
     */
    public TrasladoGrupoPasos(EmpresaRepository empresaRepository, ProductoRepository productoRepository,
            BodegaRepository bodegaRepository, TrasladoGrupoRepository trasladoRepository,
            MovimientoInventarioService movimientoService, ConsecutivoGenerator consecutivos,
            AuditoriaService auditoriaService) {
        this.empresaRepository = empresaRepository;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.trasladoRepository = trasladoRepository;
        this.movimientoService = movimientoService;
        this.consecutivos = consecutivos;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Verifica que la empresa destino sea del mismo grupo que la del contexto y distinta de ella.
     *
     * @param destinoId empresa destino pedida
     * @return la empresa destino
     * @throws NoEncontradoException si la empresa actual no tiene grupo, o la destino no existe, es la misma o es de otro
     *                               grupo (mismo mensaje en todos los casos: no se revela si existe)
     */
    @Transactional(readOnly = true)
    public Empresa exigirDestinoDelGrupo(Long destinoId) {
        Long actualId = EmpresaContexto.requerida();
        Empresa actual = empresaRepository.findById(actualId).orElseThrow(() -> new NoEncontradoException("Empresa", actualId));
        return empresaRepository.findById(destinoId)
                .filter(d -> actual.getGrupoId() != null && !d.getId().equals(actualId)
                        && actual.getGrupoId().equals(d.getGrupoId()))
                .orElseThrow(() -> new NoEncontradoException("Empresa", destinoId));
    }

    /**
     * Lee los productos de la origen que se van a enviar: los suma por producto y valida que se puedan mover.
     *
     * @param bodegaOrigenId bodega de salida
     * @param lineas         líneas pedidas
     * @return una referencia por producto distinto, en el orden pedido
     * @throws NoEncontradoException si la bodega o un producto no existen en la empresa
     * @throws NegocioException      si algo está inactivo, es un agrupador o es un servicio
     */
    @Transactional(readOnly = true)
    public List<Referencia> referenciasOrigen(Long bodegaOrigenId, List<LineaEnvioRequest> lineas) {
        Bodega b = bodegaRepository.findById(bodegaOrigenId).orElseThrow(() -> new NoEncontradoException("Bodega", bodegaOrigenId));
        if (!b.isActivo()) {
            throw new NegocioException("BODEGA_INACTIVA", "La bodega " + b.getNombre() + " está inactiva");
        }
        Map<Long, BigDecimal> cantidades = new LinkedHashMap<>();
        for (LineaEnvioRequest l : lineas) {
            cantidades.merge(l.productoId(), l.cantidad().setScale(ESCALA, RoundingMode.HALF_UP), BigDecimal::add);
        }
        List<Referencia> refs = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : cantidades.entrySet()) {
            Producto p = productoRepository.findById(e.getKey()).orElseThrow(() -> new NoEncontradoException("Producto", e.getKey()));
            if (!p.isActivo()) {
                throw new NegocioException("PRODUCTO_INACTIVO", "El producto " + p.getCodigo() + " está inactivo");
            }
            p.exigirNoAgrupador();
            p.exigirNoServicio();
            BigDecimal costo = p.costoVigente() == null ? BigDecimal.ZERO : p.costoVigente();
            refs.add(new Referencia(p.getId(), p.getCodigo(), vacioANulo(p.getCodigoBarras()), e.getValue(),
                    costo.setScale(ESCALA, RoundingMode.HALF_UP)));
        }
        return refs;
    }

    /**
     * Busca en la empresa del contexto (la destino: se llama dentro de {@code EmpresaContexto.como}) la pareja de cada
     * producto: primero por código y, si no hay, por código de barras. Un producto inactivo, agrupador o servicio no
     * sirve como pareja.
     *
     * @param refs productos de la origen
     * @return id del producto pareja por cada referencia, en el mismo orden; {@code null} si no hay
     */
    @Transactional(readOnly = true)
    public List<Long> emparejar(List<Referencia> refs) {
        List<Long> parejas = new ArrayList<>();
        for (Referencia r : refs) {
            Optional<Producto> p = productoRepository.findByCodigoIgnoreCase(r.codigo());
            if (p.filter(this::sirveDeDestino).isEmpty() && r.codigoBarras() != null) {
                p = productoRepository.findFirstByCodigoBarras(r.codigoBarras());
            }
            parejas.add(p.filter(this::sirveDeDestino).map(Producto::getId).orElse(null));
        }
        return parejas;
    }

    /**
     * Crea el traslado en la empresa origen (la del contexto): consecutivo TG, salida de stock por cada producto con su
     * movimiento y costo, y auditoría. Todo en una transacción: si el stock no alcanza no queda nada.
     *
     * @param req       envío pedido
     * @param destino   empresa destino ya verificada
     * @param refs      productos de la origen
     * @param parejas   pareja de cada producto en la destino (todas presentes)
     * @return traslado EN_TRANSITO con sus líneas
     * @throws NegocioException      {@code STOCK_INSUFICIENTE} si una bodega no alcanza
     * @throws NoEncontradoException si la destino ya no está en el mismo grupo que la origen
     */
    @Transactional
    public TrasladoGrupo registrarEnvio(EnviarTrasladoRequest req, Empresa destino, List<Referencia> refs,
            List<Long> parejas) {
        Long origenId = EmpresaContexto.requerida();
        Empresa origen = empresaRepository.findById(origenId).orElseThrow(() -> new NoEncontradoException("Empresa", origenId));
        // se vuelve a leer la destino: entre la verificación y este paso pudo cambiar de grupo
        Empresa destinoActual = empresaRepository.findById(destino.getId())
                .orElseThrow(() -> new NoEncontradoException("Empresa", destino.getId()));
        if (origen.getGrupoId() == null || !origen.getGrupoId().equals(destinoActual.getGrupoId())) {
            throw new NoEncontradoException("Empresa", destino.getId());
        }
        TrasladoGrupo t = new TrasladoGrupo();
        t.setGrupoId(origen.getGrupoId());
        t.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.TRASLADO_GRUPO));
        t.setEmpresaOrigenId(origenId);
        t.setEmpresaDestinoId(destino.getId());
        t.setBodegaOrigenId(req.bodegaOrigenId());
        t.setNota(limpiar(req.nota()));
        t.setEnviadoPorUsuarioId(SesionActual.usuario().id());
        for (int i = 0; i < refs.size(); i++) {
            Referencia r = refs.get(i);
            TrasladoGrupoLinea l = new TrasladoGrupoLinea();
            l.setProductoOrigenId(r.productoId());
            l.setProductoDestinoId(parejas.get(i));
            l.setCantidad(r.cantidad());
            l.setCostoUnitario(r.costo());
            t.agregarLinea(l);
        }
        TrasladoGrupo guardado = trasladoRepository.saveAndFlush(t);
        for (TrasladoGrupoLinea l : guardado.getLineas()) {
            movimientoService.registrarSalidaTrasladoGrupo(l.getProductoOrigenId(), req.bodegaOrigenId(), l.getCantidad(),
                    l.getCostoUnitario(), guardado.getId());
        }
        // las líneas se leen aquí, con la sesión abierta: quien llama usa el traslado ya fuera de la transacción
        int lineas = guardado.getLineas().size();
        auditoriaService.exito(AccionAuditoria.TRASLADO_GRUPO_ENVIAR, EntidadAuditada.TRASLADO_GRUPO, guardado.getId(),
                guardado.getConsecutivo() + " a la empresa " + destino.getId() + "; líneas=" + lineas);
        return guardado;
    }

    /**
     * @param p producto de la destino
     * @return {@code true} si se le puede recibir mercancía
     */
    private boolean sirveDeDestino(Producto p) {
        return p.isActivo() && !p.isEsAgrupador() && !p.isEsServicio();
    }

    /**
     * @param s texto
     * @return el texto sin espacios sobrantes, o {@code null} si queda vacío
     */
    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * @param s nota escrita por la persona
     * @return la nota limpia o {@code null}
     */
    static String limpiar(String s) {
        return vacioANulo(s);
    }
}
