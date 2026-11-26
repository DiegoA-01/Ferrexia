package com.proyecto.supply_core.reporte.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.caja.entity.Caja;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.inventario.entity.FaltanteVenta;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.FaltanteVentaRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.reporte.dto.VentaSinExistenciaFila;
import com.proyecto.supply_core.security.context.Permisos;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.venta.entity.Venta;
import com.proyecto.supply_core.venta.repository.VentaRepository;

/**
 * Reporte "Ventas sin existencia" (spec 030): cada faltante con su venta, cajero, producto, motivo y estado, para
 * corregir el inventario o comprar. Solo lectura. Quien no ve todas las ventas ve solo lo que vendió.
 */
@Service
public class VentaSinExistenciaService {

    private final FaltanteVentaRepository faltanteRepository;
    private final VentaRepository ventaRepository;
    private final CajaRepository cajaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * @param faltanteRepository faltantes
     * @param ventaRepository    ventas
     * @param cajaRepository     cajas
     * @param productoRepository productos
     * @param usuarioRepository  nombres de los cajeros
     */
    public VentaSinExistenciaService(FaltanteVentaRepository faltanteRepository, VentaRepository ventaRepository,
            CajaRepository cajaRepository, ProductoRepository productoRepository, UsuarioRepository usuarioRepository) {
        this.faltanteRepository = faltanteRepository;
        this.ventaRepository = ventaRepository;
        this.cajaRepository = cajaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * @param desde      primer día (vacío = sin límite)
     * @param hasta      último día (vacío = sin límite)
     * @param productoId producto (opcional)
     * @param estado     estado (opcional)
     * @return faltantes, del más reciente al más viejo
     * @throws ParametroInvalidoException si {@code hasta} es anterior a {@code desde}
     */
    @Transactional(readOnly = true)
    public List<VentaSinExistenciaFila> reporte(LocalDate desde, LocalDate hasta, Long productoId,
            FaltanteVenta.Estado estado) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ParametroInvalidoException("La fecha final no puede ser anterior a la inicial");
        }
        List<FaltanteVenta> faltantes = faltanteRepository.reporte(desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(), productoId, estado);
        if (!SesionActual.usuario().tienePermiso(Permisos.VENTA_VER_TODAS)) {
            Long yo = SesionActual.usuario().id();
            faltantes = faltantes.stream().filter(f -> f.getUsuarioId().equals(yo)).toList();
        }
        Map<Long, Venta> ventas = ventaRepository.findAllById(faltantes.stream().map(FaltanteVenta::getVentaId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Venta::getId, Function.identity()));
        Map<Long, String> cajas = cajaRepository.findAllById(ventas.values().stream().map(Venta::getCajaId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Caja::getId, Caja::getNombre));
        Map<Long, Producto> productos = productoRepository.findAllById(faltantes.stream()
                .map(FaltanteVenta::getProductoId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        Map<Long, String> nombres = usuarioRepository.findAllById(faltantes.stream().map(FaltanteVenta::getUsuarioId)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombreCompleto));
        return faltantes.stream().map(f -> {
            Venta v = ventas.get(f.getVentaId());
            Producto p = productos.get(f.getProductoId());
            return new VentaSinExistenciaFila(f.getId(), f.getCreadoEn(), f.getVentaId(),
                    v == null ? null : v.getConsecutivo(), v == null ? null : cajas.get(v.getCajaId()),
                    nombres.get(f.getUsuarioId()), f.getProductoId(), p == null ? null : p.getCodigo(),
                    p == null ? null : p.getNombre(), p == null ? null : p.getUnidadMedida().getCodigo(), f.getCantidad(),
                    f.getPendiente(), f.getMotivo(), f.getEstado().name(), f.getRegularizadaEn());
        }).toList();
    }
}
