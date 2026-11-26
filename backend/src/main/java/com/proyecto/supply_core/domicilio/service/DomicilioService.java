package com.proyecto.supply_core.domicilio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;
import com.proyecto.supply_core.caja.repository.MovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.cliente.entity.Cliente;
import com.proyecto.supply_core.cliente.repository.ClienteRepository;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.domicilio.dto.DomicilioDtos;
import com.proyecto.supply_core.domicilio.entity.Domicilio;
import com.proyecto.supply_core.domicilio.entity.Domicilio.Estado;
import com.proyecto.supply_core.domicilio.repository.DomicilioEventoRepository;
import com.proyecto.supply_core.domicilio.repository.DomicilioRepository;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.security.service.PermisosService;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.venta.dto.PagoVentaRequest;
import com.proyecto.supply_core.venta.dto.PrecioRequest;
import com.proyecto.supply_core.venta.entity.ListaPrecio;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.entity.VentaPago;
import com.proyecto.supply_core.venta.enums.EstadoVenta;
import com.proyecto.supply_core.venta.enums.MedioPagoVenta;
import com.proyecto.supply_core.venta.repository.ListaPrecioRepository;
import com.proyecto.supply_core.venta.repository.PrecioProductoRepository;
import com.proyecto.supply_core.venta.repository.VentaRepository;
import com.proyecto.supply_core.venta.service.AnulacionVentaService;
import com.proyecto.supply_core.venta.service.PreciosService;

/**
 * Domicilios (spec 032, D19): tablero del día, asignación del domiciliario, estados con historial, devolución o
 * cancelación (anulan la venta y el stock regresa) y cobro de la contraentrega en la caja del cajero que lo recibe.
 * También el producto servicio del envío. Quien gestiona o cobra ve todos; el domiciliario, solo los suyos.
 */
@Service
public class DomicilioService {

    /** Código del producto servicio del envío. */
    public static final String CODIGO_ENVIO = "ENVIO";
    /** Referencia de los movimientos de caja de una venta. */
    private static final String REFERENCIA_VENTA = "VENTA";

    private final DomicilioRepository domicilioRepository;
    private final DomicilioEventoRepository eventoRepository;
    private final DomicilioRegistro registro;
    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final TurnoCajaRepository turnoRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final AnulacionVentaService anulacionService;
    private final ProductoRepository productoRepository;
    private final CategoriaProductoRepository categoriaRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final ListaPrecioRepository listaRepository;
    private final PrecioProductoRepository precioRepository;
    private final PreciosService preciosService;
    private final PermisosService permisosService;
    private final AuditoriaService auditoriaService;

    /**
     * @param domicilioRepository      domicilios
     * @param eventoRepository         historial
     * @param registro                 anota los cambios
     * @param ventaRepository          ventas
     * @param clienteRepository        clientes
     * @param usuarioRepository        domiciliarios y nombres
     * @param turnoRepository          turno de quien cobra
     * @param movimientoCajaRepository libro de caja
     * @param anulacionService         anula la venta de un domicilio devuelto o cancelado
     * @param productoRepository       producto del envío
     * @param categoriaRepository      categoría del envío
     * @param unidadRepository         unidad del envío
     * @param listaRepository          lista General
     * @param precioRepository         precio del envío
     * @param preciosService           fija el precio del envío
     * @param permisosService          permisos de otro usuario (el domiciliario)
     * @param auditoriaService         registro de cambios
     */
    public DomicilioService(DomicilioRepository domicilioRepository, DomicilioEventoRepository eventoRepository,
            DomicilioRegistro registro, VentaRepository ventaRepository, ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository, TurnoCajaRepository turnoRepository,
            MovimientoCajaRepository movimientoCajaRepository, AnulacionVentaService anulacionService,
            ProductoRepository productoRepository, CategoriaProductoRepository categoriaRepository,
            UnidadMedidaRepository unidadRepository, ListaPrecioRepository listaRepository,
            PrecioProductoRepository precioRepository, PreciosService preciosService, PermisosService permisosService,
            AuditoriaService auditoriaService) {
        this.domicilioRepository = domicilioRepository;
        this.eventoRepository = eventoRepository;
        this.registro = registro;
        this.ventaRepository = ventaRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.turnoRepository = turnoRepository;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.anulacionService = anulacionService;
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.unidadRepository = unidadRepository;
        this.listaRepository = listaRepository;
        this.precioRepository = precioRepository;
        this.preciosService = preciosService;
        this.permisosService = permisosService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * @param fecha  día (vacío = hoy)
     * @param estado estado (opcional)
     * @return domicilios del día, del más reciente al más viejo
     * @throws AccesoDenegadoException si la sesión no gestiona, no cobra ni reparte
     */
    @Transactional(readOnly = true)
    public List<DomicilioDtos.Respuesta> listar(LocalDate fecha, Estado estado) {
        LocalDate dia = fecha == null ? LocalDate.now() : fecha;
        List<Domicilio> lista = domicilioRepository.buscar(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay(), estado,
                veTodos() ? null : SesionActual.usuario().id());
        return respuestas(lista, false);
    }

    /**
     * @param id domicilio
     * @return el domicilio con su historial
     * @throws NoEncontradoException si no existe o es de otro domiciliario
     */
    @Transactional(readOnly = true)
    public DomicilioDtos.Respuesta obtener(Long id) {
        return respuestas(List.of(visible(domicilioRepository.findById(id))), true).getFirst();
    }

    /**
     * Asigna el domiciliario: interno con el permiso de repartir, o externo con nombre y teléfono.
     *
     * @param id  domicilio
     * @param req domiciliario
     * @return el domicilio asignado
     * @throws NegocioException si ya salió o el usuario no reparte domicilios
     */
    @Transactional
    public DomicilioDtos.Respuesta asignar(Long id, DomicilioDtos.Asignar req) {
        Domicilio d = bloquear(id);
        if (d.getEstado() != Estado.PENDIENTE && d.getEstado() != Estado.ASIGNADO) {
            throw new NegocioException("DOMICILIO_YA_SALIO", "El domicilio " + d.getConsecutivo() + " está "
                    + d.getEstado().name().toLowerCase() + ": ya no se reasigna");
        }
        String nota;
        if (req.domiciliarioUsuarioId() != null) {
            Usuario u = usuarioRepository.findWithRolesById(req.domiciliarioUsuarioId()).filter(Usuario::isActivo)
                    .orElseThrow(() -> new NoEncontradoException("Usuario", req.domiciliarioUsuarioId()));
            if (!reparte(u)) {
                throw new NegocioException("NO_ES_DOMICILIARIO", u.getNombreCompleto() + " no tiene el permiso de repartir domicilios");
            }
            d.setDomiciliarioUsuarioId(u.getId());
            d.setDomiciliarioNombre(null);
            d.setDomiciliarioTelefono(null);
            nota = "Domiciliario: " + u.getNombreCompleto();
        } else {
            if (req.nombre() == null || req.nombre().isBlank()) {
                throw new NegocioException("DOMICILIARIO_REQUERIDO", "Elija un domiciliario o escriba el nombre del externo");
            }
            d.setDomiciliarioUsuarioId(null);
            d.setDomiciliarioNombre(req.nombre().trim());
            d.setDomiciliarioTelefono(req.telefono() == null || req.telefono().isBlank() ? null : req.telefono().trim());
            nota = "Domiciliario externo: " + d.getDomiciliarioNombre();
        }
        d.setEstado(Estado.ASIGNADO);
        registro.anotar(d, Estado.ASIGNADO.name(), SesionActual.usuario().id(), nota);
        auditoriaService.exito(AccionAuditoria.DOMICILIO_ESTADO, EntidadAuditada.VENTA, d.getVentaId(),
                d.getConsecutivo() + "; ASIGNADO; " + nota);
        return obtener(id);
    }

    /**
     * Cambia el estado: en camino y entregado (quien gestiona o el domiciliario asignado); devuelto desde en camino y
     * cancelado antes de salir (quien gestiona, con motivo), que anulan la venta.
     *
     * @param id  domicilio
     * @param req estado nuevo y nota
     * @return el domicilio
     * @throws NegocioException        si el cambio no sigue el orden de los estados o falta el motivo
     * @throws AccesoDenegadoException si la sesión no puede hacer ese cambio
     */
    @Transactional
    public DomicilioDtos.Respuesta cambiarEstado(Long id, DomicilioDtos.CambioEstado req) {
        Domicilio d = bloquear(id);
        Long yo = SesionActual.usuario().id();
        boolean gestiona = SesionActual.usuario().tienePermiso(Permisos.DOMICILIO_GESTIONAR);
        boolean esSuyo = Objects.equals(d.getDomiciliarioUsuarioId(), yo)
                && SesionActual.usuario().tienePermiso(Permisos.DOMICILIO_REPARTIR);
        Estado nuevo = req.estado();
        String nota = req.nota() == null || req.nota().isBlank() ? null : req.nota().trim();
        boolean valido = switch (nuevo) {
            case EN_CAMINO -> d.getEstado() == Estado.ASIGNADO;
            case ENTREGADO, DEVUELTO -> d.getEstado() == Estado.EN_CAMINO;
            case CANCELADO -> d.getEstado() == Estado.PENDIENTE || d.getEstado() == Estado.ASIGNADO;
            default -> false;
        };
        if (!valido) {
            throw new NegocioException("TRANSICION_INVALIDA", "El domicilio está " + d.getEstado().name().toLowerCase()
                    + ": no puede pasar a " + nuevo.name().toLowerCase());
        }
        boolean anula = nuevo == Estado.DEVUELTO || nuevo == Estado.CANCELADO;
        if (anula ? !gestiona : !(gestiona || esSuyo)) {
            throw new AccesoDenegadoException(anula ? "Solo quien gestiona domicilios los devuelve o cancela"
                    : "Solo el domiciliario asignado o quien gestiona domicilios cambia su estado");
        }
        if (anula && (nota == null || nota.length() < 5)) {
            throw new NegocioException("MOTIVO_OBLIGATORIO", "Escriba el motivo (mínimo 5 caracteres)");
        }
        d.setEstado(nuevo);
        if (nuevo == Estado.ENTREGADO) {
            d.setEntregadoEn(LocalDateTime.now());
        }
        if (anula) {
            d.setMotivo(nota);
            // el estado ya es terminal: la anulación no lo vuelve a cancelar
            domicilioRepository.saveAndFlush(d);
            anulacionService.anularPorDomicilio(d.getVentaId(), (nuevo == Estado.DEVUELTO ? "Domicilio devuelto: "
                    : "Domicilio cancelado: ") + nota);
        }
        registro.anotar(d, nuevo.name(), yo, nota);
        auditoriaService.exito(AccionAuditoria.DOMICILIO_ESTADO, EntidadAuditada.VENTA, d.getVentaId(),
                d.getConsecutivo() + "; " + nuevo + (nota == null ? "" : "; " + nota));
        return obtener(id);
    }

    /**
     * Cobra en la caja de la sesión un domicilio contraentrega ya entregado: un {@code COBRO_DOMICILIO} por medio y
     * los pagos de la venta pasan a ser los reales.
     *
     * @param id  domicilio
     * @param req pagos por medios de caja que suman el total de la venta
     * @return el domicilio cobrado
     * @throws NegocioException si no es contraentrega entregada sin cobrar, no hay turno abierto o los pagos no suman el
     *                          total
     */
    @Transactional
    public DomicilioDtos.Respuesta cobrar(Long id, DomicilioDtos.Cobro req) {
        Domicilio d = bloquear(id);
        if (d.getPago() != Domicilio.Pago.CONTRAENTREGA || d.getEstado() != Estado.ENTREGADO || d.getCobradoEn() != null) {
            throw new NegocioException("DOMICILIO_NO_COBRABLE",
                    "Solo se cobra un domicilio contraentrega entregado y sin cobrar");
        }
        Long yo = SesionActual.usuario().id();
        TurnoCaja turno = turnoRepository.abiertoDeBloqueado(yo).orElseThrow(() -> new NegocioException(
                "TURNO_NO_ABIERTO", "Abra su turno de caja para recibir el dinero del domicilio"));
        Venta v = ventaRepository.bloquear(d.getVentaId()).orElseThrow(() -> new NoEncontradoException("Venta no encontrada"));
        if (v.getEstado() != EstadoVenta.CONFIRMADA) {
            throw new NegocioException("VENTA_ANULADA", "La venta del domicilio está anulada");
        }
        if (req.pagos().stream().anyMatch(p -> !p.medio().entraACaja())) {
            throw new NegocioException("MEDIO_INVALIDO", "El domicilio se cobra con efectivo, tarjeta, transferencia o billetera");
        }
        Map<MedioPago, BigDecimal> porMedio = new EnumMap<>(MedioPago.class);
        for (PagoVentaRequest p : req.pagos()) {
            porMedio.merge(p.medio().enCaja(), p.monto().setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        }
        BigDecimal suma = porMedio.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.compareTo(v.totalAPagar()) != 0) {
            throw new NegocioException("PAGOS_NO_CUADRAN", "Los pagos suman " + suma.toPlainString() + " y el domicilio es de "
                    + v.totalAPagar().toPlainString());
        }
        porMedio.forEach((medio, monto) -> {
            MovimientoCaja m = new MovimientoCaja();
            m.setTurnoId(turno.getId());
            m.setTipo(TipoMovimientoCaja.COBRO_DOMICILIO);
            m.setMedioPago(medio);
            m.setMonto(monto);
            m.setReferenciaTipo(REFERENCIA_VENTA);
            m.setReferenciaId(v.getId());
            m.setMotivo("Domicilio " + d.getConsecutivo() + " (venta " + v.getConsecutivo() + ")");
            m.setUsuarioId(yo);
            movimientoCajaRepository.save(m);
        });
        v.getPagos().clear();
        ventaRepository.saveAndFlush(v);
        for (PagoVentaRequest p : req.pagos()) {
            VentaPago x = new VentaPago();
            x.setVenta(v);
            x.setMedio(p.medio());
            x.setMonto(p.monto().setScale(2, RoundingMode.HALF_UP));
            x.setRecibido(p.medio() == MedioPagoVenta.EFECTIVO ? x.getMonto() : null);
            x.setReferencia(p.referencia() == null || p.referencia().isBlank() ? null : p.referencia().trim());
            v.getPagos().add(x);
        }
        d.setCobradoEn(LocalDateTime.now());
        d.setCobradoTurnoId(turno.getId());
        d.setCobradoPorUsuarioId(yo);
        registro.anotar(d, "COBRADO", yo, "Turno " + turno.getId());
        auditoriaService.exito(AccionAuditoria.DOMICILIO_COBRAR, EntidadAuditada.VENTA, v.getId(), d.getConsecutivo()
                + "; venta=" + v.getConsecutivo() + "; turno=" + turno.getId() + "; pagos=" + porMedio);
        return obtener(id);
    }

    /**
     * @return el producto servicio del envío y su precio en la lista General, o vacío si no está configurado
     */
    @Transactional(readOnly = true)
    public DomicilioDtos.Envio envio() {
        Producto p = productoRepository.findByCodigoIgnoreCase(CODIGO_ENVIO).filter(Producto::isEsServicio).orElse(null);
        if (p == null) {
            return new DomicilioDtos.Envio(null, null, null, null);
        }
        BigDecimal precio = general().flatMap(l -> precioRepository.vigentesDeProducto(l.getId(), p.getId()).stream()
                .filter(x -> Objects.equals(x.getUnidadMedidaId(), p.getUnidadMedida().getId())).findFirst())
                .map(x -> x.getPrecio()).orElse(null);
        return new DomicilioDtos.Envio(p.getId(), p.getCodigo(), p.getNombre(), precio);
    }

    /**
     * Fija el precio del envío; la primera vez crea el producto servicio "Envío a domicilio" (sin stock).
     *
     * @param req precio en la lista General
     * @return el envío configurado
     * @throws NegocioException si el código ENVIO ya lo usa un producto que no es servicio
     */
    @Transactional
    public DomicilioDtos.Envio configurarEnvio(DomicilioDtos.EnvioPrecio req) {
        preciosService.exigirEdicion();
        Producto p = productoRepository.findByCodigoIgnoreCase(CODIGO_ENVIO).orElse(null);
        if (p != null && !p.isEsServicio()) {
            throw new NegocioException("CODIGO_DUPLICADO", "Ya existe un producto con el código " + CODIGO_ENVIO
                    + " que no es un servicio: cámbiele el código");
        }
        if (p == null) {
            p = new Producto();
            p.setCodigo(CODIGO_ENVIO);
            p.setNombre("Envío a domicilio");
            p.setCategoria(categoriaServicios());
            p.setUnidadMedida(unidadRepository.findByCodigoIgnoreCase("UND")
                    .orElseThrow(() -> new NegocioException("SIN_UNIDAD", "No existe la unidad UND")));
            p.setStockMinimo(BigDecimal.ZERO);
            p.setEsServicio(true);
            p.setVendible(true);
            p = productoRepository.save(p);
            auditoriaService.exito(AccionAuditoria.PRODUCTO_CREAR, EntidadAuditada.PRODUCTO, p.getId(),
                    "codigo=" + CODIGO_ENVIO + ", servicio de domicilios");
        }
        ListaPrecio general = general().orElseThrow(() -> new NegocioException("SIN_LISTA_GENERAL",
                "La empresa no tiene lista de precios General"));
        preciosService.fijarPrecio(new PrecioRequest(general.getId(), p.getId(), req.precio(), null, "Precio del envío", null));
        return envio();
    }

    /**
     * @return usuarios internos activos con el permiso de repartir domicilios, por nombre
     */
    @Transactional(readOnly = true)
    public List<DomicilioDtos.Domiciliario> domiciliarios() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.isActivo() && u.getProveedorId() == null && reparte(u))
                .map(u -> new DomicilioDtos.Domiciliario(u.getId(), u.getNombreCompleto()))
                .sorted(java.util.Comparator.comparing(DomicilioDtos.Domiciliario::nombre)).toList();
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * @return {@code true} si la sesión ve todos los domicilios (gestiona o cobra)
     * @throws AccesoDenegadoException si no gestiona, no cobra ni reparte
     */
    private boolean veTodos() {
        UsuarioAutenticado u = SesionActual.usuario();
        if (u.tienePermiso(Permisos.DOMICILIO_GESTIONAR) || u.tienePermiso(Permisos.VENTA_COBRAR)) {
            return true;
        }
        if (u.tienePermiso(Permisos.DOMICILIO_REPARTIR)) {
            return false;
        }
        throw new AccesoDenegadoException("Necesita el permiso de gestionar o repartir domicilios");
    }

    /**
     * @param encontrado domicilio buscado
     * @return el domicilio, si la sesión puede verlo
     * @throws NoEncontradoException si no existe o es de otro domiciliario
     */
    private Domicilio visible(java.util.Optional<Domicilio> encontrado) {
        Domicilio d = encontrado.orElseThrow(() -> new NoEncontradoException("Domicilio no encontrado"));
        if (!veTodos() && !Objects.equals(d.getDomiciliarioUsuarioId(), SesionActual.usuario().id())) {
            throw new NoEncontradoException("Domicilio no encontrado");
        }
        return d;
    }

    /**
     * @param id domicilio
     * @return el domicilio bloqueado
     * @throws NoEncontradoException si no es de la empresa
     */
    private Domicilio bloquear(Long id) {
        return domicilioRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Domicilio no encontrado"));
    }

    /**
     * @param u usuario
     * @return {@code true} si tiene el permiso de repartir domicilios
     */
    private boolean reparte(Usuario u) {
        return permisosService.tiene(new UsuarioAutenticado(u.getId(), u.getCorreo(), u.getNombreCompleto(),
                u.codigosRoles(), u.getProveedorId(), SesionActual.usuario().empresaId()), Permisos.DOMICILIO_REPARTIR);
    }

    /**
     * @return la lista de precios General
     */
    private java.util.Optional<ListaPrecio> general() {
        return listaRepository.findAllByOrderByEsGeneralDescNombreAsc().stream().filter(ListaPrecio::isEsGeneral).findFirst();
    }

    /**
     * @return la categoría "Servicios" de la empresa (la crea si no existe)
     */
    private CategoriaProducto categoriaServicios() {
        return categoriaRepository.findByNombreIgnoreCase("Servicios").orElseGet(() -> {
            CategoriaProducto c = new CategoriaProducto();
            c.setNombre("Servicios");
            return categoriaRepository.save(c);
        });
    }

    /**
     * @param lista    domicilios
     * @param conEventos {@code true} para incluir el historial
     * @return respuestas con venta, cliente y nombres
     */
    private List<DomicilioDtos.Respuesta> respuestas(List<Domicilio> lista, boolean conEventos) {
        Map<Long, Venta> ventas = ventaRepository.findAllById(lista.stream().map(Domicilio::getVentaId).toList()).stream()
                .collect(Collectors.toMap(Venta::getId, Function.identity()));
        Map<Long, String> clientes = clienteRepository.findAllById(ventas.values().stream().map(Venta::getClienteId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Cliente::getId, Cliente::getNombre));
        Set<Long> ids = new HashSet<>();
        lista.forEach(d -> ids.add(d.getDomiciliarioUsuarioId()));
        Map<Long, List<com.proyecto.supply_core.domicilio.entity.DomicilioEvento>> eventos = new java.util.HashMap<>();
        if (conEventos) {
            lista.forEach(d -> {
                var es = eventoRepository.findByDomicilioIdOrderByIdAsc(d.getId());
                es.forEach(e -> ids.add(e.getUsuarioId()));
                eventos.put(d.getId(), es);
            });
        }
        ids.remove(null);
        Map<Long, String> nombres = usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombreCompleto));
        return lista.stream().map(d -> {
            Venta v = ventas.get(d.getVentaId());
            String domiciliario = d.getDomiciliarioUsuarioId() != null ? nombres.get(d.getDomiciliarioUsuarioId())
                    : d.getDomiciliarioNombre();
            List<DomicilioDtos.Evento> historial = eventos.getOrDefault(d.getId(), List.of()).stream()
                    .map(e -> new DomicilioDtos.Evento(e.getEstado(), nombres.get(e.getUsuarioId()), e.getNota(),
                            e.getCreadoEn())).toList();
            return new DomicilioDtos.Respuesta(d.getId(), d.getConsecutivo(), d.getVentaId(),
                    v == null ? null : v.getConsecutivo(), v == null ? null : clientes.get(v.getClienteId()),
                    v == null ? null : v.totalAPagar(), d.getDireccion(), d.getTelefono(), d.getReferencia(), d.getPago(),
                    d.getEstado(), d.getDomiciliarioUsuarioId(), domiciliario, d.getDomiciliarioTelefono(), d.getMotivo(),
                    d.getCreadoEn(), d.getEntregadoEn(), d.getCobradoEn(),
                    d.getPago() == Domicilio.Pago.CONTRAENTREGA && d.getEstado() == Estado.ENTREGADO && d.getCobradoEn() == null,
                    historial);
        }).toList();
    }
}
