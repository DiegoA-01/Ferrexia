package com.proyecto.supply_core.grupo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.AnularTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.DestinoResponse;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.EnviarTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.LineaRecepcionTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.LineaTrasladoResponse;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.RecibirTrasladoRequest;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.TrasladoDetalleResponse;
import com.proyecto.supply_core.grupo.dto.TrasladoGrupoDtos.TrasladoResumenResponse;
import com.proyecto.supply_core.grupo.entity.TrasladoGrupo;
import com.proyecto.supply_core.grupo.entity.TrasladoGrupoLinea;
import com.proyecto.supply_core.grupo.enums.EstadoTrasladoGrupo;
import com.proyecto.supply_core.grupo.repository.TrasladoGrupoRepository;
import com.proyecto.supply_core.grupo.service.TrasladoGrupoPasos.Referencia;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.service.CostoPromedioService;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.service.PermisosService;

/**
 * Traslados de mercancía entre empresas del mismo grupo (spec 035). Las tablas del traslado no tienen {@code @TenantId}:
 * TODO acceso filtra a mano por la empresa del contexto como origen o destino, y un traslado ajeno es 404.
 * <p>Cada paso toca el stock de UNA sola empresa, dentro de su propia transacción: enviar y anular corren en la origen,
 * recibir en la destino. La destino solo se lee (emparejar productos) con {@code EmpresaContexto.como}. El traslado se
 * bloquea ({@code FOR UPDATE}) al recibir y anular, así no se recibe dos veces ni se recibe uno anulado.</p>
 */
@Service
public class TrasladoGrupoService {

    private static final Logger log = LoggerFactory.getLogger(TrasladoGrupoService.class);
    private static final int ESCALA = 4;
    private static final String ENVIADO = "ENVIADO";
    private static final String RECIBIDO = "RECIBIDO";

    private final EmpresaRepository empresaRepository;
    private final TrasladoGrupoRepository trasladoRepository;
    private final TrasladoGrupoPasos pasos;
    private final ProductoRepository productoRepository;
    private final BodegaRepository bodegaRepository;
    private final MovimientoInventarioService movimientoService;
    private final CostoPromedioService costoPromedioService;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    /** El propio servicio a través de su proxy: así una llamada interna abre su transacción. {@code null} en pruebas sin Spring. */
    private TrasladoGrupoService propio;

    /**
     * @param empresaRepository    empresas (globales)
     * @param trasladoRepository   traslados
     * @param pasos                pasos transaccionales del envío
     * @param productoRepository   productos de la empresa del contexto
     * @param bodegaRepository     bodegas de la empresa del contexto
     * @param movimientoService    único punto que mueve el stock
     * @param costoPromedioService costo promedio de la empresa que recibe
     * @param auditoriaService     auditoría de la empresa que actúa
     * @param notificacionService  aviso a la destino
     */
    public TrasladoGrupoService(EmpresaRepository empresaRepository, TrasladoGrupoRepository trasladoRepository,
            TrasladoGrupoPasos pasos, ProductoRepository productoRepository, BodegaRepository bodegaRepository,
            MovimientoInventarioService movimientoService, CostoPromedioService costoPromedioService,
            AuditoriaService auditoriaService, NotificacionService notificacionService) {
        this.empresaRepository = empresaRepository;
        this.trasladoRepository = trasladoRepository;
        this.pasos = pasos;
        this.productoRepository = productoRepository;
        this.bodegaRepository = bodegaRepository;
        this.movimientoService = movimientoService;
        this.costoPromedioService = costoPromedioService;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /**
     * Recibe el proxy del propio servicio (con {@code @Lazy} para no crear un ciclo al arrancar).
     *
     * @param propio este mismo servicio, con sus transacciones
     */
    @org.springframework.beans.factory.annotation.Autowired
    void setPropio(@org.springframework.context.annotation.Lazy TrasladoGrupoService propio) {
        this.propio = propio;
    }

    /**
     * @return el servicio con transacciones para las llamadas internas (sin Spring, el mismo objeto)
     */
    private TrasladoGrupoService propio() {
        return propio != null ? propio : this;
    }

    /**
     * Empresas del mismo grupo que la del contexto, sin ella. Solo id y nombre: nada de datos de negocio.
     *
     * @return destinos posibles por nombre; vacío si la empresa no tiene grupo
     */
    @Transactional(readOnly = true)
    public List<DestinoResponse> destinos() {
        Long actualId = EmpresaContexto.requerida();
        Empresa actual = empresaRepository.findById(actualId).orElseThrow(() -> new NoEncontradoException("Empresa", actualId));
        if (actual.getGrupoId() == null) {
            return List.of();
        }
        return empresaRepository.findByGrupoIdOrderByIdAsc(actual.getGrupoId()).stream()
                .filter(e -> !e.getId().equals(actualId))
                .map(e -> new DestinoResponse(e.getId(), e.getRazonSocial()))
                .sorted((a, b) -> a.nombre().compareToIgnoreCase(b.nombre())).toList();
    }

    /**
     * Envía mercancía a otra empresa del grupo: empareja cada producto por código (o código de barras) en la destino,
     * saca el stock de la origen al costo vigente y deja el traslado EN_TRANSITO. Avisa a quien recibe en la destino.
     * No es transaccional por sí mismo: la lectura de la destino y la salida de la origen abren su transacción cada una.
     *
     * @param req destino, bodega de salida, líneas y nota
     * @return traslado creado con sus líneas
     * @throws NoEncontradoException si la destino no es del mismo grupo (o es la misma), o la bodega o un producto no existen
     * @throws NegocioException      {@code PRODUCTOS_SIN_PAREJA} con la lista de códigos sin pareja; {@code STOCK_INSUFICIENTE}
     *                               si la bodega no alcanza; o producto inactivo, agrupador o servicio
     */
    public TrasladoDetalleResponse enviar(EnviarTrasladoRequest req) {
        Empresa destino = pasos.exigirDestinoDelGrupo(req.empresaDestinoId());
        List<Referencia> refs = pasos.referenciasOrigen(req.bodegaOrigenId(), req.lineas());
        List<Long> parejas = EmpresaContexto.como(destino.getId(), () -> pasos.emparejar(refs));
        List<String> sinPareja = new java.util.ArrayList<>();
        for (int i = 0; i < refs.size(); i++) {
            if (parejas.get(i) == null) {
                sinPareja.add(refs.get(i).codigo());
            }
        }
        if (!sinPareja.isEmpty()) {
            throw new NegocioException("PRODUCTOS_SIN_PAREJA", "La empresa destino no tiene estos productos (se buscan por "
                    + "código o código de barras): " + String.join(", ", sinPareja));
        }
        TrasladoGrupo t = pasos.registrarEnvio(req, destino, refs, parejas);
        avisarDestino(t, refs.size());
        return propio().obtener(t.getId());
    }

    /**
     * Recibe un traslado en la empresa destino: bloquea el traslado, exige que origen y destino sigan en el mismo grupo,
     * mete lo recibido a la bodega al costo de la línea (actualiza el costo promedio) y deja el faltante (enviado menos
     * recibido). Estado RECIBIDO.
     *
     * @param id  traslado
     * @param req bodega destino, lo recibido de cada línea y nota
     * @return traslado recibido con sus líneas
     * @throws NoEncontradoException si el traslado no es de la empresa del contexto (origen ni destino) o la bodega no existe
     * @throws NegocioException      {@code SOLO_DESTINO_RECIBE} si lo pide la origen, {@code TRASLADO_NO_EN_TRANSITO} si ya se
     *                               recibió o se anuló, {@code TRASLADO_FUERA_DEL_GRUPO} si dejaron de ser del mismo grupo,
     *                               {@code LINEAS_INCOMPLETAS} o {@code CANTIDAD_INVALIDA} si lo recibido no cuadra
     */
    @Transactional
    public TrasladoDetalleResponse recibir(Long id, RecibirTrasladoRequest req) {
        Long actualId = EmpresaContexto.requerida();
        TrasladoGrupo t = bloquear(id, actualId);
        if (!t.getEmpresaDestinoId().equals(actualId)) {
            throw new NegocioException("SOLO_DESTINO_RECIBE", "Solo la empresa destino puede recibir el traslado");
        }
        exigirEnTransito(t);
        exigirMismoGrupo(t);
        Bodega bodega = bodegaRepository.findById(req.bodegaDestinoId())
                .orElseThrow(() -> new NoEncontradoException("Bodega", req.bodegaDestinoId()));
        if (!bodega.isActivo()) {
            throw new NegocioException("BODEGA_INACTIVA", "La bodega " + bodega.getNombre() + " está inactiva");
        }
        Map<Long, BigDecimal> recibido = recibidoPorLinea(t, req.lineas());

        for (TrasladoGrupoLinea l : t.getLineas()) {
            BigDecimal q = recibido.get(l.getId());
            l.setCantidadRecibida(q);
            if (q.signum() > 0) {
                Producto p = productoRepository.findById(l.getProductoDestinoId())
                        .orElseThrow(() -> new NoEncontradoException("Producto", l.getProductoDestinoId()));
                costoPromedioService.registrarCompra(p, q, l.getCostoUnitario());
                movimientoService.registrarEntradaTrasladoGrupo(p.getId(), bodega.getId(), q, l.getCostoUnitario(), null,
                        t.getId());
            }
        }
        t.setBodegaDestinoId(bodega.getId());
        t.setEstado(EstadoTrasladoGrupo.RECIBIDO);
        t.setRecibidoPorUsuarioId(SesionActual.usuario().id());
        t.setRecibidoEn(LocalDateTime.now());
        t.setNotaRecepcion(TrasladoGrupoPasos.limpiar(req.nota()));
        trasladoRepository.saveAndFlush(t);
        BigDecimal faltante = t.getLineas().stream().map(TrasladoGrupoLinea::faltante).reduce(BigDecimal.ZERO, BigDecimal::add);
        auditoriaService.exito(AccionAuditoria.TRASLADO_GRUPO_RECIBIR, EntidadAuditada.TRASLADO_GRUPO, t.getId(),
                t.getConsecutivo() + " de la empresa " + t.getEmpresaOrigenId() + "; faltante total="
                        + faltante.toPlainString());
        return detalle(t, actualId);
    }

    /**
     * Anula un traslado EN_TRANSITO desde la empresa origen: el stock vuelve a su bodega con su movimiento, y el estado
     * pasa a ANULADO.
     *
     * @param id  traslado
     * @param req motivo obligatorio
     * @return traslado anulado
     * @throws NoEncontradoException si el traslado no es de la empresa del contexto
     * @throws NegocioException      {@code SOLO_ORIGEN_ANULA} si lo pide la destino, {@code TRASLADO_NO_EN_TRANSITO} si ya se
     *                               recibió o se anuló
     */
    @Transactional
    public TrasladoDetalleResponse anular(Long id, AnularTrasladoRequest req) {
        Long actualId = EmpresaContexto.requerida();
        TrasladoGrupo t = bloquear(id, actualId);
        if (!t.getEmpresaOrigenId().equals(actualId)) {
            throw new NegocioException("SOLO_ORIGEN_ANULA", "Solo la empresa que envió el traslado puede anularlo");
        }
        exigirEnTransito(t);
        String motivo = req.motivo().trim();
        for (TrasladoGrupoLinea l : t.getLineas()) {
            Producto p = productoRepository.findById(l.getProductoOrigenId())
                    .orElseThrow(() -> new NoEncontradoException("Producto", l.getProductoOrigenId()));
            costoPromedioService.registrarCompra(p, l.getCantidad(), l.getCostoUnitario());
            movimientoService.registrarEntradaTrasladoGrupo(p.getId(), t.getBodegaOrigenId(), l.getCantidad(),
                    l.getCostoUnitario(), "Anulación del traslado " + t.getConsecutivo() + ": " + motivo, t.getId());
        }
        t.setEstado(EstadoTrasladoGrupo.ANULADO);
        t.setAnuladoPorUsuarioId(SesionActual.usuario().id());
        t.setAnuladoEn(LocalDateTime.now());
        t.setMotivoAnulacion(motivo);
        trasladoRepository.saveAndFlush(t);
        auditoriaService.exito(AccionAuditoria.TRASLADO_GRUPO_ANULAR, EntidadAuditada.TRASLADO_GRUPO, t.getId(),
                t.getConsecutivo() + "; motivo=" + motivo);
        return detalle(t, actualId);
    }

    /**
     * Traslados enviados y recibidos por la empresa del contexto, los más nuevos primero.
     *
     * @param pageable página (el orden es siempre el más nuevo primero)
     * @return página de traslados
     */
    @Transactional(readOnly = true)
    public PageResponse<TrasladoResumenResponse> listar(Pageable pageable) {
        exigirAlgunPermiso();
        Long actualId = EmpresaContexto.requerida();
        Page<TrasladoGrupo> pagina = trasladoRepository.deLaEmpresa(actualId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        Map<Long, String> nombres = nombresDeEmpresas(pagina.getContent());
        return PageResponse.of(pagina, t -> resumen(t, actualId, nombres));
    }

    /**
     * Un traslado con sus líneas, visto desde la empresa del contexto.
     *
     * @param id traslado
     * @return traslado con líneas (código, nombre, cantidades y faltante)
     * @throws NoEncontradoException si el traslado no es de la empresa del contexto (origen ni destino)
     */
    @Transactional(readOnly = true)
    public TrasladoDetalleResponse obtener(Long id) {
        exigirAlgunPermiso();
        Long actualId = EmpresaContexto.requerida();
        TrasladoGrupo t = trasladoRepository.deLaEmpresa(id, actualId)
                .orElseThrow(() -> new NoEncontradoException("Traslado", id));
        return detalle(t, actualId);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Las consultas piden uno de los dos permisos de traslados (enviar o recibir); el controller no puede anotar dos.
     *
     * @throws AccesoDenegadoException {@code PERMISO_REQUERIDO} si la persona no tiene ninguno
     */
    private void exigirAlgunPermiso() {
        if (!PermisosService.sesionTiene(Permisos.TRASLADO_GRUPO_ENVIAR)
                && !PermisosService.sesionTiene(Permisos.TRASLADO_GRUPO_RECIBIR)) {
            throw new AccesoDenegadoException("PERMISO_REQUERIDO", "No tiene permisos para esta operación");
        }
    }

    /**
     * Avisa, dentro de la empresa destino, a quien puede recibir traslados (los roles con el permiso sembrado). Un fallo
     * del aviso no deshace el envío.
     *
     * @param t         traslado ya guardado
     * @param productos cantidad de productos enviados
     */
    private void avisarDestino(TrasladoGrupo t, int productos) {
        try {
            String texto = "La empresa " + nombreEmpresa(t.getEmpresaOrigenId()) + " envió el traslado "
                    + t.getConsecutivo() + " con " + productos + " producto(s). Espera su recepción.";
            EmpresaContexto.como(t.getEmpresaDestinoId(), () -> notificacionService.notificarRoles(
                    List.of(Roles.ALMACENISTA, Roles.ADMIN_SISTEMA, "ADMINISTRADOR"),
                    EventoNotificacion.TRASLADO_GRUPO_ENVIADO, texto));
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar a la empresa {} del traslado {}: {}", t.getEmpresaDestinoId(), t.getConsecutivo(),
                    e.getMessage());
        }
    }

    /**
     * Carga el traslado con bloqueo y solo si la empresa es su origen o su destino.
     *
     * @param id        traslado
     * @param actualId  empresa del contexto
     * @return traslado bloqueado
     * @throws NoEncontradoException si no existe o es de otras empresas
     */
    private TrasladoGrupo bloquear(Long id, Long actualId) {
        return trasladoRepository.deLaEmpresaBloqueado(id, actualId)
                .orElseThrow(() -> new NoEncontradoException("Traslado", id));
    }

    /**
     * @param t traslado bloqueado
     * @throws NegocioException {@code TRASLADO_NO_EN_TRANSITO} si ya se recibió o se anuló
     */
    private void exigirEnTransito(TrasladoGrupo t) {
        if (t.getEstado() != EstadoTrasladoGrupo.EN_TRANSITO) {
            throw new NegocioException("TRASLADO_NO_EN_TRANSITO",
                    "El traslado " + t.getConsecutivo() + " ya está " + t.getEstado());
        }
    }

    /**
     * Origen y destino deben seguir en el grupo del traslado: si alguna se sacó o se pasó a otro grupo no se recibe.
     *
     * @param t traslado
     * @throws NegocioException {@code TRASLADO_FUERA_DEL_GRUPO} si ya no son del mismo grupo
     */
    private void exigirMismoGrupo(TrasladoGrupo t) {
        Long gOrigen = empresaRepository.findById(t.getEmpresaOrigenId()).map(Empresa::getGrupoId).orElse(null);
        Long gDestino = empresaRepository.findById(t.getEmpresaDestinoId()).map(Empresa::getGrupoId).orElse(null);
        if (gOrigen == null || !gOrigen.equals(gDestino) || !gOrigen.equals(t.getGrupoId())) {
            throw new NegocioException("TRASLADO_FUERA_DEL_GRUPO",
                    "El origen y el destino del traslado ya no pertenecen al mismo grupo");
        }
    }

    /**
     * Valida lo recibido: cada línea del traslado exactamente una vez, de 0 a lo enviado.
     *
     * @param t      traslado
     * @param lineas lo recibido por línea
     * @return cantidad recibida por id de línea, con escala 4
     */
    private Map<Long, BigDecimal> recibidoPorLinea(TrasladoGrupo t, List<LineaRecepcionTrasladoRequest> lineas) {
        Map<Long, BigDecimal> recibido = new HashMap<>();
        Set<Long> repetidas = new HashSet<>();
        for (LineaRecepcionTrasladoRequest l : lineas) {
            if (recibido.put(l.lineaId(), l.cantidadRecibida().setScale(ESCALA, RoundingMode.HALF_UP)) != null) {
                repetidas.add(l.lineaId());
            }
        }
        Set<Long> propias = t.getLineas().stream().map(TrasladoGrupoLinea::getId).collect(Collectors.toSet());
        if (!repetidas.isEmpty() || !recibido.keySet().equals(propias)) {
            throw new NegocioException("LINEAS_INCOMPLETAS",
                    "Debe indicar lo recibido de cada línea del traslado, una sola vez");
        }
        for (TrasladoGrupoLinea l : t.getLineas()) {
            BigDecimal q = recibido.get(l.getId());
            if (q.signum() < 0 || q.compareTo(l.getCantidad()) > 0) {
                throw new NegocioException("CANTIDAD_INVALIDA", "Lo recibido debe estar entre 0 y "
                        + l.getCantidad().toPlainString() + " (línea " + l.getId() + ")");
            }
        }
        return recibido;
    }

    /**
     * @param ids empresa
     * @return razón social de la empresa o su id si no existe
     */
    private String nombreEmpresa(Long ids) {
        return empresaRepository.findById(ids).map(Empresa::getRazonSocial).orElse(String.valueOf(ids));
    }

    /**
     * @param traslados traslados de una página
     * @return razón social por id de las empresas que aparecen
     */
    private Map<Long, String> nombresDeEmpresas(List<TrasladoGrupo> traslados) {
        Set<Long> ids = new HashSet<>();
        traslados.forEach(t -> {
            ids.add(t.getEmpresaOrigenId());
            ids.add(t.getEmpresaDestinoId());
        });
        return empresaRepository.findAllById(ids).stream().collect(Collectors.toMap(Empresa::getId, Empresa::getRazonSocial));
    }

    /**
     * @param t        traslado
     * @param actualId empresa que consulta
     * @param nombres  razón social por empresa
     * @return fila de la lista
     */
    private TrasladoResumenResponse resumen(TrasladoGrupo t, Long actualId, Map<Long, String> nombres) {
        return new TrasladoResumenResponse(t.getId(), t.getConsecutivo(), t.getEstado(),
                t.getEmpresaOrigenId().equals(actualId) ? ENVIADO : RECIBIDO, t.getEmpresaOrigenId(),
                nombres.get(t.getEmpresaOrigenId()), t.getEmpresaDestinoId(), nombres.get(t.getEmpresaDestinoId()),
                t.getLineas().size(), t.getNota(), t.getEnviadoEn(), t.getRecibidoEn(), t.getAnuladoEn());
    }

    /**
     * Arma el detalle con los productos de la empresa que consulta (los de la otra no se pueden leer).
     *
     * @param t        traslado
     * @param actualId empresa que consulta
     * @return detalle con líneas
     */
    private TrasladoDetalleResponse detalle(TrasladoGrupo t, Long actualId) {
        boolean esOrigen = t.getEmpresaOrigenId().equals(actualId);
        Map<Long, String> nombres = nombresDeEmpresas(List.of(t));
        Set<Long> ids = t.getLineas().stream()
                .map(l -> esOrigen ? l.getProductoOrigenId() : l.getProductoDestinoId()).collect(Collectors.toSet());
        Map<Long, Producto> productos = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));
        List<LineaTrasladoResponse> lineas = t.getLineas().stream().map(l -> {
            Long pid = esOrigen ? l.getProductoOrigenId() : l.getProductoDestinoId();
            Producto p = productos.get(pid);
            return new LineaTrasladoResponse(l.getId(), pid, p == null ? null : p.getCodigo(),
                    p == null ? null : p.getNombre(), l.getCantidad(), l.getCantidadRecibida(), l.faltante(),
                    l.getCostoUnitario());
        }).toList();
        Long bodega = esOrigen ? t.getBodegaOrigenId() : t.getBodegaDestinoId();
        return new TrasladoDetalleResponse(resumen(t, actualId, nombres), bodega, t.getNotaRecepcion(),
                t.getMotivoAnulacion(), lineas);
    }
}
