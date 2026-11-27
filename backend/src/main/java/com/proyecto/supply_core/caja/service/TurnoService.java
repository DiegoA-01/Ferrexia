package com.proyecto.supply_core.caja.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.dto.AbrirTurnoRequest;
import com.proyecto.supply_core.caja.dto.AprobarTurnoRequest;
import com.proyecto.supply_core.caja.dto.CerrarTurnoRequest;
import com.proyecto.supply_core.caja.dto.ConteoDenominacion;
import com.proyecto.supply_core.caja.dto.EsperadoTurnoResponse;
import com.proyecto.supply_core.caja.dto.MovimientoCajaRequest;
import com.proyecto.supply_core.caja.dto.MovimientoCajaResponse;
import com.proyecto.supply_core.caja.dto.TurnoResponse;
import com.proyecto.supply_core.caja.entity.Caja;
import com.proyecto.supply_core.caja.entity.CajaUsuario;
import com.proyecto.supply_core.caja.entity.CategoriaMovimientoCaja;
import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.AccionAutorizada;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoCategoriaMovimiento;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.caja.repository.CajaUsuarioRepository;
import com.proyecto.supply_core.caja.repository.CategoriaMovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.MovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.repository.VentaRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turnos de caja (spec 003): apertura con base, retiros, gastos e ingresos con autorización, cierre ciego y aprobación
 * de diferencias.
 * <ul>
 *   <li>Un solo turno abierto por caja y por cajero (lo garantiza la base de datos, V33).</li>
 *   <li>Todo peso que entra o sale queda en {@code movimiento_caja}; el esperado es la suma de esos registros.</li>
 *   <li>Cierre ciego: el cajero no ve el esperado hasta guardar lo contado; Java calcula y guarda las diferencias.</li>
 *   <li>Si alguna diferencia pasa la tolerancia, la aprueba un supervisor que no sea el cajero.</li>
 * </ul>
 */
@Service
public class TurnoService {

    /** Movimientos que el cajero ve con el turno abierto (los demás revelarían cuánto debería tener). */
    private static final Set<TipoMovimientoCaja> VISIBLES_AL_CAJERO = Set.of(TipoMovimientoCaja.BASE,
            TipoMovimientoCaja.RETIRO, TipoMovimientoCaja.GASTO, TipoMovimientoCaja.INGRESO);

    /** Referencia de los movimientos que pertenecen a una venta (la misma que escribe VentaService). */
    private static final String REFERENCIA_VENTA = "VENTA";

    private final VentaRepository ventaRepository;
    private final TurnoCajaRepository turnoRepository;
    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacionService autorizacionService;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;
    private final CategoriaMovimientoCajaService categoriaService;
    private final CategoriaMovimientoCajaRepository categoriaRepository;
    private final CajaUsuarioRepository cajaUsuarioRepository;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * @param turnoRepository      turnos
     * @param cajaRepository       cajas
     * @param movimientoRepository libro de caja
     * @param empresaRepository    tolerancia de diferencia
     * @param usuarioRepository    nombres de cajeros y supervisores
     * @param autorizacionService  autorizaciones de un solo uso
     * @param notificacionService  aviso de diferencias por aprobar
     * @param auditoriaService     registro de cada acción
     * @param categoriaService     validación de la categoría de un ingreso o egreso (spec 021)
     * @param categoriaRepository  nombres de las categorías en el libro de caja
     * @param cajaUsuarioRepository cajas asignadas a cada persona (spec 025)
     * @param ventaRepository      ventas a las que apuntan los movimientos (consecutivo y total)
     */
    public TurnoService(TurnoCajaRepository turnoRepository, CajaRepository cajaRepository,
            MovimientoCajaRepository movimientoRepository, EmpresaRepository empresaRepository,
            UsuarioRepository usuarioRepository, AutorizacionService autorizacionService,
            NotificacionService notificacionService, AuditoriaService auditoriaService,
            CategoriaMovimientoCajaService categoriaService, CategoriaMovimientoCajaRepository categoriaRepository,
            CajaUsuarioRepository cajaUsuarioRepository, VentaRepository ventaRepository) {
        this.cajaUsuarioRepository = cajaUsuarioRepository;
        this.ventaRepository = ventaRepository;
        this.turnoRepository = turnoRepository;
        this.cajaRepository = cajaRepository;
        this.movimientoRepository = movimientoRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.autorizacionService = autorizacionService;
        this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService;
        this.categoriaService = categoriaService;
        this.categoriaRepository = categoriaRepository;
    }

    /**
     * Abre el turno de la sesión en una caja con su base en efectivo.
     *
     * @param req caja y base
     * @return turno abierto
     * @throws NoEncontradoException si la caja no es de la empresa
     * @throws NegocioException      si la caja está inactiva u ocupada, o el cajero ya tiene un turno abierto
     */
    @Transactional
    public TurnoResponse abrir(AbrirTurnoRequest req) {
        Long cajero = SesionActual.usuario().id();
        Caja caja = cajaRepository.conDetalle(req.cajaId()).orElseThrow(() -> new NoEncontradoException("Caja no encontrada"));
        if (!caja.isActiva()) {
            throw new NegocioException("CAJA_INACTIVA", "La caja " + caja.getNombre() + " está inactiva");
        }
        // Spec 025: quien tiene cajas asignadas solo abre en ellas; sin ninguna asignada puede usar todas.
        Set<Long> asignadas = cajaUsuarioRepository.findByUsuarioId(cajero).stream().map(CajaUsuario::getCajaId)
                .collect(java.util.stream.Collectors.toSet());
        if (!asignadas.isEmpty() && !asignadas.contains(caja.getId())) {
            throw new AccesoDenegadoException("CAJA_NO_ASIGNADA", "No tiene asignada la caja " + caja.getNombre());
        }
        turnoRepository.abiertoDe(cajero).ifPresent(t -> {
            throw new NegocioException("TURNO_ABIERTO", "Ya tienes un turno abierto en " + t.getCaja().getNombre());
        });
        if (turnoRepository.existsByCajaIdAndEstado(caja.getId(), EstadoTurno.ABIERTO)) {
            throw new NegocioException("CAJA_OCUPADA", "La caja " + caja.getNombre() + " ya tiene un turno abierto");
        }
        TurnoCaja t = new TurnoCaja();
        t.setCaja(caja);
        t.setCajeroUsuarioId(cajero);
        t.setAbiertoEn(LocalDateTime.now());
        t.setBaseApertura(req.baseApertura().setScale(2, RoundingMode.HALF_UP));
        try {
            turnoRepository.saveAndFlush(t);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            // Dos aperturas simultáneas de la misma caja: MySQL responde con la llave duplicada o, a veces, con un
            // interbloqueo. En los dos casos otro turno ganó, así que se responde 409 y no un error interno.
            throw new NegocioException("TURNO_ABIERTO", "La caja o el cajero ya tienen un turno abierto");
        }
        if (t.getBaseApertura().signum() > 0) {
            movimientoRepository.save(movimiento(t, TipoMovimientoCaja.BASE, t.getBaseApertura(), "Base de apertura",
                    null, null));
        }
        auditoriaService.exito(AccionAuditoria.TURNO_ABRIR, EntidadAuditada.TURNO_CAJA, t.getId(),
                "caja=" + caja.getId() + "; base=" + t.getBaseApertura());
        return respuesta(t);
    }

    /**
     * @return el turno abierto de la sesión
     * @throws NoEncontradoException si no tiene uno
     */
    @Transactional(readOnly = true)
    public TurnoResponse actual() {
        TurnoCaja t = turnoRepository.abiertoDe(SesionActual.usuario().id())
                .orElseThrow(() -> new NoEncontradoException("No tienes un turno abierto"));
        return respuesta(t);
    }

    /**
     * Retiro a la bóveda, gasto menor o ingreso de efectivo, con autorización de un supervisor. El gasto y el ingreso
     * llevan una categoría activa de su tipo; el retiro puede llevar una de egreso (spec 021).
     *
     * @param turnoId turno
     * @param req     tipo, monto, motivo, soporte, autorización y categoría
     * @return turno con el movimiento
     * @throws NegocioException si el turno no está abierto o no es de la sesión, el tipo no es válido, falta la
     *                          categoría o no sirve, no hay efectivo suficiente o la autorización no sirve
     */
    @Transactional
    public TurnoResponse registrarMovimiento(Long turnoId, MovimientoCajaRequest req) {
        TurnoCaja t = abiertoDeLaSesion(turnoId);
        AccionAutorizada accion = switch (req.tipo()) {
            case RETIRO -> AccionAutorizada.RETIRO;
            case GASTO -> AccionAutorizada.GASTO;
            case INGRESO -> AccionAutorizada.INGRESO;
            default -> throw new NegocioException("TIPO_NO_PERMITIDO",
                    "Aquí solo se registran retiros, gastos e ingresos de efectivo");
        };
        CategoriaMovimientoCaja categoria = categoria(req);
        BigDecimal monto = req.monto().setScale(2, RoundingMode.HALF_UP);
        boolean sale = req.tipo() != TipoMovimientoCaja.INGRESO;
        if (sale && movimientoRepository.saldo(t.getId(), MedioPago.EFECTIVO).compareTo(monto) < 0) {
            throw new NegocioException("EFECTIVO_INSUFICIENTE", "No hay suficiente efectivo en la caja para ese monto");
        }
        // Spec 025 (D15): con el permiso de hacerlo sin PIN no se pide autorización; sin él, el PIN como siempre.
        boolean sinPin = SesionActual.usuario().tienePermiso(permisoSinPin(req.tipo()));
        Long supervisor = sinPin ? null : autorizacionService.consumir(req.autorizacion(), accion, t.getId(), null);
        MovimientoCaja m = movimiento(t, req.tipo(), sale ? monto.negate() : monto, req.motivo().trim(),
                req.soporte() == null || req.soporte().isBlank() ? null : req.soporte().trim(), supervisor);
        m.setCategoriaId(categoria == null ? null : categoria.getId());
        movimientoRepository.save(m);
        auditoriaService.exito(AccionAuditoria.TURNO_MOVIMIENTO, EntidadAuditada.TURNO_CAJA, t.getId(),
                req.tipo() + " " + monto + "; movimiento=" + m.getId()
                        + (sinPin ? "; sinPin=true" : "; autorizó=" + supervisor)
                        + (categoria == null ? "" : "; categoría=" + categoria.getNombre()));
        return respuesta(t);
    }

    /**
     * @param tipo retiro, gasto o ingreso
     * @return el permiso que deja registrarlo sin el PIN del supervisor (spec 025)
     */
    static String permisoSinPin(TipoMovimientoCaja tipo) {
        return switch (tipo) {
            case RETIRO -> Permisos.CAJA_RETIRO_SIN_PIN;
            case GASTO -> Permisos.CAJA_GASTO_SIN_PIN;
            default -> Permisos.CAJA_INGRESO_SIN_PIN;
        };
    }

    /**
     * Cierre ciego: guarda lo contado, calcula el esperado y la diferencia por medio, y deja el turno cerrado. Si
     * alguna diferencia pasa la tolerancia, avisa a los supervisores para que la aprueben. Si llega el conteo por
     * billete y moneda (spec 023), Java suma el efectivo y guarda el conteo con el turno.
     *
     * @param turnoId turno
     * @param req     contado (total o conteo), otros medios y observación
     * @return turno cerrado con esperado, contado y diferencias (tiquete Z)
     * @throws NegocioException si falta el efectivo, el conteo no cuadra con el total escrito o trae una denominación
     *                          que no existe o repetida
     */
    @Transactional
    public TurnoResponse cerrar(Long turnoId, CerrarTurnoRequest req) {
        TurnoCaja t = abiertoDeLaSesion(turnoId);
        if (req.contadoOtros() != null) {
            req.contadoOtros().forEach((medio, valor) -> {
                if (valor != null && valor.signum() < 0) {
                    throw new NegocioException("CONTADO_NEGATIVO", "Lo contado en " + medio + " no puede ser negativo");
                }
            });
        }
        BigDecimal efectivo = efectivoContado(req);
        Map<MedioPago, BigDecimal> esperado = CalculoCaja.esperado(movimientoRepository.findByTurnoIdOrderByIdAsc(t.getId()));
        Map<MedioPago, BigDecimal> contado = CalculoCaja.contado(efectivo, req.contadoOtros());
        Map<MedioPago, BigDecimal> diferencias = CalculoCaja.diferencias(contado, esperado);
        BigDecimal tolerancia = empresaRepository.findById(EmpresaContexto.requerida())
                .orElseThrow(() -> new NoEncontradoException("Empresa no encontrada")).getToleranciaDiferenciaCaja();
        boolean requiere = CalculoCaja.requiereAprobacion(diferencias, tolerancia);
        t.setEstado(EstadoTurno.CERRADO);
        t.setCerradoEn(LocalDateTime.now());
        t.setContadoEfectivo(contado.get(MedioPago.EFECTIVO));
        t.setContadoOtros(aJson(sinEfectivo(contado)));
        t.setConteoDenominaciones(req.conteo() == null || req.conteo().isEmpty() ? null
                : json.writeValueAsString(conteoLimpio(req.conteo())));
        t.setEsperadoEfectivo(esperado.get(MedioPago.EFECTIVO));
        t.setEsperadoOtros(aJson(sinEfectivo(esperado)));
        t.setDiferencias(aJson(diferencias));
        t.setDiferencia(CalculoCaja.total(diferencias));
        t.setRequiereAprobacion(requiere);
        t.setObservacionCierre(req.observacion() == null || req.observacion().isBlank() ? null : req.observacion().trim());
        turnoRepository.saveAndFlush(t);
        auditoriaService.exito(AccionAuditoria.TURNO_CERRAR, EntidadAuditada.TURNO_CAJA, t.getId(),
                "diferencia=" + t.getDiferencia() + "; porMedio=" + t.getDiferencias() + "; tolerancia=" + tolerancia
                        + "; requiereAprobacion=" + requiere);
        if (requiere) {
            String mensaje = "El turno " + t.getId() + " de " + t.getCaja().getNombre() + " cerró con una diferencia de $ "
                    + t.getDiferencia().toPlainString() + ": revísala y apruébala en Cajas y turnos";
            notificacionService.notificarRol(Roles.SUPERVISOR_VENTAS, EventoNotificacion.TURNO_DIFERENCIA, mensaje);
            notificacionService.notificarRol(Roles.ADMIN_SISTEMA, EventoNotificacion.TURNO_DIFERENCIA, mensaje);
        }
        return respuesta(t);
    }

    /**
     * Un supervisor explica y aprueba la diferencia de un turno cerrado. Nadie aprueba su propio turno.
     *
     * @param turnoId turno
     * @param req     explicación
     * @return turno aprobado
     * @throws NegocioException si el turno no espera aprobación o es del mismo supervisor
     */
    @Transactional
    public TurnoResponse aprobar(Long turnoId, AprobarTurnoRequest req) {
        TurnoCaja t = turnoRepository.bloquear(turnoId).orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (t.getEstado() != EstadoTurno.CERRADO || !t.isRequiereAprobacion()) {
            throw new NegocioException("SIN_APROBACION_PENDIENTE", "Este turno no tiene una diferencia por aprobar");
        }
        Long supervisor = SesionActual.usuario().id();
        if (t.getCajeroUsuarioId().equals(supervisor)) {
            throw new NegocioException("APRUEBA_LO_SUYO", "Nadie aprueba la diferencia de su propio turno");
        }
        t.setEstado(EstadoTurno.APROBADO);
        t.setAprobadoPorUsuarioId(supervisor);
        t.setAprobadoEn(LocalDateTime.now());
        t.setExplicacionAprobacion(req.explicacion().trim());
        turnoRepository.saveAndFlush(t);
        auditoriaService.exito(AccionAuditoria.TURNO_APROBAR, EntidadAuditada.TURNO_CAJA, t.getId(),
                "diferencia=" + t.getDiferencia() + "; explicación=" + t.getExplicacionAprobacion());
        return respuesta(t);
    }

    /**
     * @param estado     filtro por estado
     * @param pendientes solo los que esperan aprobación
     * @param cajaId     filtro por caja
     * @param pageable   página
     * @return turnos con su resumen (sin movimientos)
     */
    @Transactional(readOnly = true)
    public PageResponse<TurnoResponse> buscar(EstadoTurno estado, boolean pendientes, Long cajaId, Pageable pageable) {
        var pagina = turnoRepository.buscar(estado, pendientes, cajaId, pageable);
        Map<Long, String> nombres = nombres(pagina.getContent().stream()
                .flatMap(t -> java.util.stream.Stream.of(t.getCajeroUsuarioId(), t.getAprobadoPorUsuarioId())).toList());
        return PageResponse.of(pagina, t -> armar(t, false, List.of(), nombres, Map.of(), Map.of()));
    }

    /**
     * Detalle de un turno: el supervisor, el dueño y auditoría ven todos; el cajero solo los suyos, y con el turno
     * abierto no ve lo esperado ni las ventas.
     *
     * @param turnoId turno
     * @return turno con su libro de caja
     * @throws NoEncontradoException si no existe o es de otro cajero y la sesión no supervisa
     */
    @Transactional(readOnly = true)
    public TurnoResponse obtener(Long turnoId) {
        TurnoCaja t = turnoRepository.conCaja(turnoId).orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (!supervisa() && !t.getCajeroUsuarioId().equals(SesionActual.usuario().id())) {
            throw new NoEncontradoException("Turno no encontrado");
        }
        return respuesta(t);
    }

    /**
     * Lo que debería haber en el turno ahora mismo (spec 021, decisión D20). El cajero no lo pide: cierra a ciegas y ve
     * lo esperado después de guardar su conteo. Lo consultan quienes supervisan (el controlador limita los roles).
     *
     * @param turnoId turno
     * @return esperado por medio de pago y total; en un turno cerrado, el que se guardó al cerrar
     * @throws NoEncontradoException si el turno no es de la empresa
     */
    @Transactional(readOnly = true)
    public EsperadoTurnoResponse esperado(Long turnoId) {
        TurnoCaja t = turnoRepository.conCaja(turnoId).orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        Map<MedioPago, BigDecimal> esperado = t.getEstado() == EstadoTurno.ABIERTO
                ? CalculoCaja.esperado(movimientoRepository.findByTurnoIdOrderByIdAsc(t.getId()))
                : conEfectivo(t.getEsperadoEfectivo(), deJson(t.getEsperadoOtros()));
        return new EsperadoTurnoResponse(t.getId(), t.getEstado(), esperado, CalculoCaja.total(esperado));
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * @param req movimiento pedido
     * @return la categoría validada, o {@code null} en un retiro sin categoría
     * @throws NegocioException {@code CATEGORIA_OBLIGATORIA} si un gasto o un ingreso llega sin ella, o
     *                          {@code CATEGORIA_INVALIDA} si no sirve
     */
    private CategoriaMovimientoCaja categoria(MovimientoCajaRequest req) {
        TipoCategoriaMovimiento tipo = req.tipo() == TipoMovimientoCaja.INGRESO ? TipoCategoriaMovimiento.INGRESO
                : TipoCategoriaMovimiento.EGRESO;
        if (req.categoriaId() == null) {
            if (req.tipo() == TipoMovimientoCaja.RETIRO) {
                return null;
            }
            throw new NegocioException("CATEGORIA_OBLIGATORIA", "Elija la categoría del "
                    + (tipo == TipoCategoriaMovimiento.INGRESO ? "ingreso" : "gasto"));
        }
        return categoriaService.paraMovimiento(req.categoriaId(), tipo);
    }

    /**
     * @param turnoId turno
     * @return el turno bloqueado, abierto y de la sesión
     */
    private TurnoCaja abiertoDeLaSesion(Long turnoId) {
        TurnoCaja t = turnoRepository.bloquear(turnoId).orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (!t.getCajeroUsuarioId().equals(SesionActual.usuario().id())) {
            throw new AccesoDenegadoException("Solo el cajero del turno registra movimientos y lo cierra");
        }
        if (t.getEstado() != EstadoTurno.ABIERTO) {
            throw new NegocioException("TURNO_NO_ABIERTO", "El turno ya está cerrado");
        }
        return t;
    }

    /**
     * @param t turno
     * @return respuesta según quién mira: con el turno abierto, al cajero solo se le muestran base, retiros, gastos e
     *         ingresos (no las ventas ni el esperado)
     */
    private TurnoResponse respuesta(TurnoCaja t) {
        List<MovimientoCaja> movimientos = movimientoRepository.findByTurnoIdOrderByIdAsc(t.getId());
        Set<Long> ids = new HashSet<>();
        ids.add(t.getCajeroUsuarioId());
        ids.add(t.getAprobadoPorUsuarioId());
        movimientos.forEach(m -> {
            ids.add(m.getUsuarioId());
            ids.add(m.getAutorizadoPorUsuarioId());
        });
        Map<Long, String> nombres = nombres(ids);
        Map<Long, String> categorias = new HashMap<>();
        List<Long> idsCategoria = movimientos.stream().map(MovimientoCaja::getCategoriaId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (!idsCategoria.isEmpty()) {
            categoriaRepository.findAllById(idsCategoria).forEach(c -> categorias.put(c.getId(), c.getNombre()));
        }
        boolean ocultar = t.getEstado() == EstadoTurno.ABIERTO && !supervisa();
        List<MovimientoCaja> visibles = ocultar
                ? movimientos.stream().filter(m -> VISIBLES_AL_CAJERO.contains(m.getTipo())).toList()
                : movimientos;
        boolean debeRetirar = t.getEstado() == EstadoTurno.ABIERTO && CalculoCaja.debeRetirar(
                CalculoCaja.esperado(movimientos).get(MedioPago.EFECTIVO), t.getCaja().getTopeEfectivo());
        return armar(t, debeRetirar, visibles, nombres, categorias, ventasDe(visibles));
    }

    /**
     * @param movimientos movimientos que se muestran
     * @return las ventas a las que apuntan (referencia {@code VENTA}), por id; el consecutivo y el total salen de aquí
     */
    private Map<Long, Venta> ventasDe(List<MovimientoCaja> movimientos) {
        List<Long> ids = movimientos.stream().filter(m -> REFERENCIA_VENTA.equals(m.getReferenciaTipo()))
                .map(MovimientoCaja::getReferenciaId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Venta> ventas = new HashMap<>();
        if (!ids.isEmpty()) {
            ventaRepository.findAllById(ids).forEach(v -> ventas.put(v.getId(), v));
        }
        return ventas;
    }

    /**
     * @param m      movimiento
     * @param ventas ventas por id
     * @return la venta del movimiento, o {@code null} si no pertenece a una
     */
    private static Venta ventaDe(MovimientoCaja m, Map<Long, Venta> ventas) {
        return REFERENCIA_VENTA.equals(m.getReferenciaTipo()) && m.getReferenciaId() != null
                ? ventas.get(m.getReferenciaId()) : null;
    }

    /**
     * @param t           turno
     * @param debeRetirar marca de tope de efectivo
     * @param movimientos movimientos que se muestran
     * @param nombres     nombres de usuario por id
     * @param categorias  nombres de categoría por id
     * @param ventas      ventas a las que apuntan los movimientos, por id
     * @return respuesta
     */
    private TurnoResponse armar(TurnoCaja t, boolean debeRetirar, List<MovimientoCaja> movimientos,
            Map<Long, String> nombres, Map<Long, String> categorias, Map<Long, Venta> ventas) {
        boolean cerrado = t.getEstado() != EstadoTurno.ABIERTO;
        Map<MedioPago, BigDecimal> esperado = null;
        Map<MedioPago, BigDecimal> contado = null;
        if (cerrado) {
            esperado = conEfectivo(t.getEsperadoEfectivo(), deJson(t.getEsperadoOtros()));
            contado = conEfectivo(t.getContadoEfectivo(), deJson(t.getContadoOtros()));
        }
        List<MovimientoCajaResponse> lista = movimientos.stream().map(m -> new MovimientoCajaResponse(m.getId(),
                m.getTipo(), m.getMedioPago(), m.getMonto(), m.getMotivo(), m.getSoporte(), nombres.get(m.getUsuarioId()),
                m.getAutorizadoPorUsuarioId() == null ? null : nombres.get(m.getAutorizadoPorUsuarioId()),
                m.getCreadoEn(), m.getCategoriaId() == null ? null : categorias.get(m.getCategoriaId()),
                m.getReferenciaTipo(), m.getReferenciaId(),
                ventaDe(m, ventas) == null ? null : ventaDe(m, ventas).getConsecutivo(),
                ventaDe(m, ventas) == null ? null : ventaDe(m, ventas).getTotal())).toList();
        return new TurnoResponse(t.getId(), t.getCaja().getId(), t.getCaja().getNombre(),
                t.getCaja().getBodega() == null ? null : t.getCaja().getBodega().getNombre(), t.getCajeroUsuarioId(),
                nombres.get(t.getCajeroUsuarioId()), t.getEstado(), t.getAbiertoEn(), t.getBaseApertura(), debeRetirar,
                t.getCerradoEn(), esperado, contado, cerrado ? deJson(t.getDiferencias()) : null,
                cerrado ? t.getDiferencia() : null, t.isRequiereAprobacion(), t.getObservacionCierre(),
                t.getAprobadoPorUsuarioId() == null ? null : nombres.get(t.getAprobadoPorUsuarioId()), t.getAprobadoEn(),
                t.getExplicacionAprobacion(), lista, cerrado ? conteoDeJson(t.getConteoDenominaciones()) : null);
    }

    /**
     * @param t             turno
     * @param tipo          tipo
     * @param monto         monto con signo
     * @param motivo        motivo
     * @param soporte       soporte
     * @param autorizadoPor supervisor que autorizó
     * @return movimiento en efectivo del cajero de la sesión
     */
    private static MovimientoCaja movimiento(TurnoCaja t, TipoMovimientoCaja tipo, BigDecimal monto, String motivo,
            String soporte, Long autorizadoPor) {
        MovimientoCaja m = new MovimientoCaja();
        m.setTurnoId(t.getId());
        m.setTipo(tipo);
        m.setMedioPago(MedioPago.EFECTIVO);
        m.setMonto(monto);
        m.setMotivo(motivo);
        m.setSoporte(soporte);
        m.setUsuarioId(SesionActual.usuario().id());
        m.setAutorizadoPorUsuarioId(autorizadoPor);
        return m;
    }

    /**
     * @return {@code true} si la sesión ve los turnos de todas las cajas (supervisor de ventas, dueño o auditor)
     */
    private static boolean supervisa() {
        return SesionActual.usuario().tienePermiso(Permisos.TURNO_BUSCAR);
    }

    /**
     * @param ids usuarios ({@code null} se ignora)
     * @return nombre por id
     */
    private Map<Long, String> nombres(java.util.Collection<Long> ids) {
        Map<Long, String> nombres = new HashMap<>();
        List<Long> validos = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (!validos.isEmpty()) {
            for (Usuario u : usuarioRepository.findAllById(validos)) {
                nombres.put(u.getId(), u.getNombreCompleto());
            }
        }
        return nombres;
    }

    /**
     * @param mapa valores por medio
     * @return el mismo mapa sin el efectivo
     */
    private static Map<MedioPago, BigDecimal> sinEfectivo(Map<MedioPago, BigDecimal> mapa) {
        Map<MedioPago, BigDecimal> copia = new EnumMap<>(mapa);
        copia.remove(MedioPago.EFECTIVO);
        return copia;
    }

    /**
     * @param efectivo efectivo
     * @param otros    otros medios
     * @return mapa con todos los medios
     */
    private static Map<MedioPago, BigDecimal> conEfectivo(BigDecimal efectivo, Map<MedioPago, BigDecimal> otros) {
        Map<MedioPago, BigDecimal> mapa = new EnumMap<>(MedioPago.class);
        mapa.put(MedioPago.EFECTIVO, efectivo);
        mapa.putAll(otros);
        return mapa;
    }

    /**
     * @param mapa valores por medio
     * @return JSON
     */
    private String aJson(Map<MedioPago, BigDecimal> mapa) {
        return json.writeValueAsString(mapa);
    }

    /**
     * @param texto JSON del conteo guardado
     * @return filas del conteo o {@code null} si el turno se cerró escribiendo solo el total
     */
    private List<ConteoDenominacion> conteoDeJson(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return json.readValue(texto, new TypeReference<List<ConteoDenominacion>>() { });
    }

    /**
     * @param req cierre pedido
     * @return efectivo contado: la suma del conteo si viene, o el total escrito
     * @throws NegocioException {@code CONTADO_OBLIGATORIO} si no viene ni el total ni el conteo, o
     *                          {@code CONTEO_NO_CUADRA} si vienen los dos y no coinciden
     */
    private static BigDecimal efectivoContado(CerrarTurnoRequest req) {
        if (req.conteo() == null || req.conteo().isEmpty()) {
            if (req.contadoEfectivo() == null) {
                throw new NegocioException("CONTADO_OBLIGATORIO", "Escriba el efectivo contado o cuente los billetes y monedas");
            }
            return req.contadoEfectivo();
        }
        BigDecimal suma = Denominaciones.total(req.conteo());
        if (req.contadoEfectivo() != null && req.contadoEfectivo().compareTo(suma) != 0) {
            throw new NegocioException("CONTEO_NO_CUADRA", "El conteo de billetes y monedas suma $ " + suma.toPlainString()
                    + " y el efectivo escrito es $ " + req.contadoEfectivo().toPlainString());
        }
        return suma;
    }

    /**
     * @param conteo filas del conteo
     * @return conteo limpio (sin filas en cero, referencia recortada) para guardar
     */
    private static List<ConteoDenominacion> conteoLimpio(List<ConteoDenominacion> conteo) {
        return conteo.stream().filter(f -> f.cantidad() != null && f.cantidad() > 0
                || f.referencia() != null && !f.referencia().isBlank())
                .map(f -> new ConteoDenominacion(f.tipo(), f.valor(), f.cantidad() == null ? 0 : f.cantidad(),
                        f.referencia() == null || f.referencia().isBlank() ? null : f.referencia().trim()))
                .toList();
    }

    /**
     * @param texto JSON guardado ({@code null} = vacío)
     * @return valores por medio
     */
    private Map<MedioPago, BigDecimal> deJson(String texto) {
        if (texto == null || texto.isBlank()) {
            return new EnumMap<>(MedioPago.class);
        }
        Map<MedioPago, BigDecimal> mapa = new EnumMap<>(MedioPago.class);
        mapa.putAll(json.readValue(texto, new TypeReference<Map<MedioPago, BigDecimal>>() { }));
        return mapa;
    }
}
