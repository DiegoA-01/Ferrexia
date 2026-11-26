package com.proyecto.supply_core.reporte.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.reporte.dto.CajaReporte;
import com.proyecto.supply_core.reporte.dto.VentasTurnoReporte;

/**
 * Reporte de caja (spec 009): lo que pasó en los turnos de un periodo. Solo lectura, con {@code empresa_id} a mano en
 * cada consulta. Lo ven supervisión, el dueño y auditoría.
 */
@Service
public class CajaReporteService {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc consultas de solo lectura
     */
    public CajaReporteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param desde primer día (vacío = primer día del mes en curso)
     * @param hasta último día (vacío = hoy)
     * @return reporte de caja
     */
    @Transactional(readOnly = true)
    public CajaReporte reporte(LocalDate desde, LocalDate hasta) {
        LocalDate[] p = VentaReporteService.periodo(desde, hasta);
        Object[] a = { EmpresaContexto.requerida(), Timestamp.valueOf(p[0].atStartOfDay()),
                Timestamp.valueOf(p[1].plusDays(1).atStartOfDay()) };

        List<Object[]> crudos = new ArrayList<>();
        List<CajaReporte.Turno> turnos = jdbc.query("""
                SELECT t.id, t.cajero_usuario_id, c.nombre AS caja, u.nombre_completo AS cajero, t.estado, t.abierto_en,
                       t.cerrado_en, t.esperado_efectivo, t.contado_efectivo, t.diferencia, t.requiere_aprobacion,
                       ua.nombre_completo AS aprobado_por, t.explicacion_aprobacion
                FROM turno_caja t
                JOIN caja c ON c.id = t.caja_id
                JOIN usuario u ON u.id = t.cajero_usuario_id
                LEFT JOIN usuario ua ON ua.id = t.aprobado_por_usuario_id
                WHERE t.empresa_id = ? AND t.abierto_en >= ? AND t.abierto_en < ?
                ORDER BY t.abierto_en DESC, t.id DESC
                """, (rs, i) -> {
                    String estado = rs.getString("estado");
                    boolean porAprobar = rs.getBoolean("requiere_aprobacion") && "CERRADO".equals(estado);
                    CajaReporte.Turno t = new CajaReporte.Turno(rs.getLong("id"), rs.getString("caja"),
                            rs.getString("cajero"), estado, fecha(rs, "abierto_en"), fecha(rs, "cerrado_en"),
                            rs.getBigDecimal("esperado_efectivo"), rs.getBigDecimal("contado_efectivo"),
                            rs.getBigDecimal("diferencia"), porAprobar, rs.getString("aprobado_por"),
                            rs.getString("explicacion_aprobacion"));
                    crudos.add(new Object[] { rs.getLong("cajero_usuario_id"), t });
                    return t;
                }, a);

        List<CajaReporte.Cajero> porCajero = porCajero(crudos);

        List<CajaReporte.Movimiento> movimientos = jdbc.query("""
                SELECT m.tipo, c.nombre AS caja, m.medio_pago, ABS(m.monto) AS monto, m.motivo, m.creado_en,
                       u.nombre_completo AS registro, ua.nombre_completo AS autorizo
                FROM movimiento_caja m
                JOIN turno_caja t ON t.id = m.turno_id
                JOIN caja c ON c.id = t.caja_id
                JOIN usuario u ON u.id = m.usuario_id
                LEFT JOIN usuario ua ON ua.id = m.autorizado_por_usuario_id
                WHERE m.empresa_id = ? AND m.tipo IN ('RETIRO', 'GASTO') AND m.creado_en >= ? AND m.creado_en < ?
                ORDER BY m.creado_en DESC, m.id DESC
                """, (rs, i) -> new CajaReporte.Movimiento(rs.getString("tipo"), rs.getString("caja"),
                        rs.getString("medio_pago"), rs.getBigDecimal("monto"), rs.getString("motivo"),
                        rs.getString("registro"), rs.getString("autorizo"), fecha(rs, "creado_en")), a);

        List<CajaReporte.Anulacion> anulaciones = jdbc.query("""
                SELECT v.id, v.consecutivo, v.total + v.redondeo AS total, v.motivo_anulacion, v.anulada_en,
                       u.nombre_completo AS anulo, ua.nombre_completo AS autorizo
                FROM venta v
                LEFT JOIN usuario u ON u.id = v.anulada_por_usuario_id
                LEFT JOIN usuario ua ON ua.id = v.anulacion_autorizada_por_usuario_id
                WHERE v.empresa_id = ? AND v.estado = 'ANULADA' AND v.anulada_en >= ? AND v.anulada_en < ?
                ORDER BY v.anulada_en DESC, v.id DESC
                """, (rs, i) -> new CajaReporte.Anulacion(rs.getLong("id"), rs.getString("consecutivo"),
                        rs.getBigDecimal("total"), rs.getString("motivo_anulacion"), rs.getString("anulo"),
                        rs.getString("autorizo"), fecha(rs, "anulada_en")), a);

        List<CajaReporte.Autorizado> autorizados = jdbc.query("""
                SELECT v.id, v.consecutivo, CONCAT(p.codigo, ' - ', p.nombre) AS producto, l.descuento_pct,
                       l.descuento_valor, v.confirmada_en, uc.nombre_completo AS cajero, ua.nombre_completo AS autorizo
                FROM venta_linea l
                JOIN venta v ON v.id = l.venta_id
                JOIN producto p ON p.id = l.producto_id
                JOIN usuario uc ON uc.id = v.cajero_usuario_id
                JOIN usuario ua ON ua.id = l.autorizado_por_usuario_id
                WHERE v.empresa_id = ? AND v.estado = 'CONFIRMADA' AND l.autorizado_por_usuario_id IS NOT NULL
                  AND v.confirmada_en >= ? AND v.confirmada_en < ?
                ORDER BY v.confirmada_en DESC, l.id
                """, (rs, i) -> {
                    BigDecimal pct = rs.getBigDecimal("descuento_pct");
                    return new CajaReporte.Autorizado(rs.getLong("id"), rs.getString("consecutivo"),
                            rs.getString("producto"), pct != null && pct.signum() > 0 ? "Descuento" : "Precio bajo el mínimo",
                            pct, rs.getBigDecimal("descuento_valor"), rs.getString("cajero"), rs.getString("autorizo"),
                            fecha(rs, "confirmada_en"));
                }, a);

        BigDecimal faltantes = porCajero.stream().map(CajaReporte.Cajero::faltantes).reduce(CERO, BigDecimal::add);
        BigDecimal sobrantes = porCajero.stream().map(CajaReporte.Cajero::sobrantes).reduce(CERO, BigDecimal::add);
        CajaReporte.Totales totales = new CajaReporte.Totales(turnos.size(), faltantes.add(sobrantes), faltantes,
                sobrantes, turnos.stream().filter(CajaReporte.Turno::porAprobar).count(),
                suma(movimientos.stream().filter(m -> "RETIRO".equals(m.tipo())).map(CajaReporte.Movimiento::monto).toList()),
                suma(movimientos.stream().filter(m -> "GASTO".equals(m.tipo())).map(CajaReporte.Movimiento::monto).toList()),
                suma(anulaciones.stream().map(CajaReporte.Anulacion::total).toList()),
                suma(autorizados.stream().map(CajaReporte.Autorizado::descuentoValor).toList()));
        return new CajaReporte(p[0], p[1], turnos, porCajero, movimientos, anulaciones, autorizados, totales);
    }

    /**
     * Datos fijos de un turno leídos de {@code turno_caja} para las ventas por turno.
     *
     * @param id         turno
     * @param caja       caja
     * @param cajero     cajero
     * @param estado     estado
     * @param abiertoEn  apertura
     * @param cerradoEn  cierre o {@code null}
     * @param diferencia diferencia guardada al cerrar o {@code null}
     */
    record BaseTurno(Long id, String caja, String cajero, String estado, LocalDateTime abiertoEn,
            LocalDateTime cerradoEn, BigDecimal diferencia) {
    }

    /**
     * Suma del libro de caja de un turno para un tipo y un medio.
     *
     * @param turnoId turno
     * @param tipo    tipo de movimiento
     * @param medio   medio de pago
     * @param monto   suma con signo (lo que sale, en negativo)
     */
    record SumaLibro(Long turnoId, String tipo, String medio, BigDecimal monto) {
    }

    /**
     * Ventas por turno (spec 023): por cada turno abierto en el periodo, sus ventas por medio de pago, devoluciones,
     * ingresos, egresos, esperado, contado y diferencia. La base de datos agrupa el libro de caja por turno, tipo y
     * medio; Java arma las cifras con las mismas sumas del cuadre.
     *
     * @param desde  primer día (vacío = primer día del mes en curso)
     * @param hasta  último día (vacío = hoy)
     * @param cajaId solo esa caja ({@code null} = todas)
     * @return ventas por turno del periodo
     */
    @Transactional(readOnly = true)
    public VentasTurnoReporte ventasPorTurno(LocalDate desde, LocalDate hasta, Long cajaId) {
        LocalDate[] p = VentaReporteService.periodo(desde, hasta);
        Long empresa = EmpresaContexto.requerida();
        Timestamp ini = Timestamp.valueOf(p[0].atStartOfDay());
        Timestamp fin = Timestamp.valueOf(p[1].plusDays(1).atStartOfDay());
        String filtroCaja = cajaId == null ? "" : " AND t.caja_id = ?";
        Object[] a = cajaId == null ? new Object[] { empresa, ini, fin } : new Object[] { empresa, ini, fin, cajaId };

        List<BaseTurno> bases = jdbc.query("SELECT t.id, c.nombre AS caja, u.nombre_completo AS cajero, t.estado,"
                + " t.abierto_en, t.cerrado_en, t.diferencia"
                + " FROM turno_caja t JOIN caja c ON c.id = t.caja_id JOIN usuario u ON u.id = t.cajero_usuario_id"
                + " WHERE t.empresa_id = ? AND t.abierto_en >= ? AND t.abierto_en < ?" + filtroCaja
                + " ORDER BY t.abierto_en DESC, t.id DESC",
                (rs, i) -> new BaseTurno(rs.getLong("id"), rs.getString("caja"), rs.getString("cajero"),
                        rs.getString("estado"), fecha(rs, "abierto_en"), fecha(rs, "cerrado_en"),
                        rs.getBigDecimal("diferencia")), a);

        List<SumaLibro> sumas = jdbc.query("SELECT m.turno_id, m.tipo, m.medio_pago, SUM(m.monto) AS monto"
                + " FROM movimiento_caja m JOIN turno_caja t ON t.id = m.turno_id AND t.empresa_id = m.empresa_id"
                + " WHERE m.empresa_id = ? AND t.abierto_en >= ? AND t.abierto_en < ?" + filtroCaja
                + " GROUP BY m.turno_id, m.tipo, m.medio_pago",
                (rs, i) -> new SumaLibro(rs.getLong("turno_id"), rs.getString("tipo"), rs.getString("medio_pago"),
                        rs.getBigDecimal("monto")), a);

        return ventasPorTurno(p[0], p[1], bases, sumas);
    }

    /**
     * Arma las ventas por turno en Java (se prueba sin base de datos).
     *
     * @param desde primer día
     * @param hasta último día
     * @param bases turnos del periodo, en el orden en que se muestran
     * @param sumas libro de caja agrupado por turno, tipo y medio
     * @return reporte con sus totales
     */
    static VentasTurnoReporte ventasPorTurno(LocalDate desde, LocalDate hasta, List<BaseTurno> bases,
            List<SumaLibro> sumas) {
        Map<Long, List<SumaLibro>> porTurno = new HashMap<>();
        sumas.forEach(s -> porTurno.computeIfAbsent(s.turnoId(), k -> new ArrayList<>()).add(s));

        List<VentasTurnoReporte.Turno> turnos = new ArrayList<>();
        Map<String, BigDecimal> ventasPeriodo = new LinkedHashMap<>();
        BigDecimal totalVentas = CERO;
        BigDecimal devoluciones = CERO;
        BigDecimal ingresos = CERO;
        BigDecimal egresos = CERO;
        BigDecimal diferencia = CERO;
        for (BaseTurno b : bases) {
            Map<String, BigDecimal> ventas = new HashMap<>();
            BigDecimal anulaciones = CERO;
            BigDecimal dev = CERO;
            BigDecimal abonos = CERO;
            BigDecimal ing = CERO;
            BigDecimal gastos = CERO;
            BigDecimal retiros = CERO;
            BigDecimal esperado = CERO;
            for (SumaLibro s : porTurno.getOrDefault(b.id(), List.of())) {
                BigDecimal monto = s.monto() == null ? CERO : s.monto().setScale(2, RoundingMode.HALF_UP);
                esperado = esperado.add(monto);
                switch (s.tipo()) {
                    case "VENTA" -> ventas.merge(s.medio(), monto, BigDecimal::add);
                    case "ANULACION" -> {
                        ventas.merge(s.medio(), monto, BigDecimal::add);
                        anulaciones = anulaciones.add(monto.abs());
                    }
                    case "DEVOLUCION" -> dev = dev.add(monto.abs());
                    case "ABONO_CARTERA" -> abonos = abonos.add(monto);
                    case "INGRESO" -> ing = ing.add(monto);
                    case "GASTO" -> gastos = gastos.add(monto.abs());
                    case "RETIRO" -> retiros = retiros.add(monto.abs());
                    default -> {
                        // BASE: solo cuenta en el esperado.
                    }
                }
            }
            Map<String, BigDecimal> ventasOrdenadas = new LinkedHashMap<>();
            for (MedioPago m : MedioPago.values()) {
                BigDecimal v = ventas.get(m.name());
                if (v != null) {
                    ventasOrdenadas.put(m.name(), v);
                    ventasPeriodo.merge(m.name(), v, BigDecimal::add);
                }
            }
            BigDecimal suma = ventasOrdenadas.values().stream().reduce(CERO, BigDecimal::add);
            boolean cerrado = b.cerradoEn() != null && !"ABIERTO".equals(b.estado()) && b.diferencia() != null;
            BigDecimal dif = cerrado ? b.diferencia().setScale(2, RoundingMode.HALF_UP) : null;
            turnos.add(new VentasTurnoReporte.Turno(b.id(), b.caja(), b.cajero(), b.estado(), b.abiertoEn(),
                    b.cerradoEn(), ventasOrdenadas, suma, anulaciones, dev, abonos, ing, gastos, retiros, esperado,
                    cerrado ? esperado.add(dif) : null, dif));
            totalVentas = totalVentas.add(suma);
            devoluciones = devoluciones.add(dev);
            ingresos = ingresos.add(ing).add(abonos);
            egresos = egresos.add(gastos).add(retiros);
            diferencia = cerrado ? diferencia.add(dif) : diferencia;
        }
        Map<String, BigDecimal> ventasMedio = new LinkedHashMap<>();
        for (MedioPago m : MedioPago.values()) {
            BigDecimal v = ventasPeriodo.get(m.name());
            if (v != null) {
                ventasMedio.put(m.name(), v);
            }
        }
        return new VentasTurnoReporte(desde, hasta, turnos, new VentasTurnoReporte.Totales(turnos.size(), ventasMedio,
                totalVentas, devoluciones, ingresos, egresos, diferencia));
    }

    /**
     * @param crudos pares (cajero, turno)
     * @return diferencias por cajero, del mayor faltante al menor
     */
    static List<CajaReporte.Cajero> porCajero(List<Object[]> crudos) {
        Map<Long, List<CajaReporte.Turno>> grupos = new LinkedHashMap<>();
        Map<Long, String> nombres = new LinkedHashMap<>();
        for (Object[] c : crudos) {
            CajaReporte.Turno t = (CajaReporte.Turno) c[1];
            grupos.computeIfAbsent((Long) c[0], k -> new ArrayList<>()).add(t);
            nombres.putIfAbsent((Long) c[0], t.cajero());
        }
        List<CajaReporte.Cajero> filas = new ArrayList<>();
        grupos.forEach((id, lista) -> {
            BigDecimal faltantes = suma(lista.stream().map(CajaReporte.Turno::diferencia)
                    .filter(d -> d != null && d.signum() < 0).toList());
            BigDecimal sobrantes = suma(lista.stream().map(CajaReporte.Turno::diferencia)
                    .filter(d -> d != null && d.signum() > 0).toList());
            filas.add(new CajaReporte.Cajero(id, nombres.get(id), lista.size(),
                    lista.stream().filter(t -> t.cerradoEn() != null).count(), faltantes.add(sobrantes), faltantes,
                    sobrantes, lista.stream().filter(CajaReporte.Turno::porAprobar).count()));
        });
        filas.sort(Comparator.comparing(CajaReporte.Cajero::faltantes).thenComparing(CajaReporte.Cajero::cajero));
        return filas;
    }

    /**
     * @param valores valores ({@code null} cuenta como 0)
     * @return suma con 2 decimales
     */
    private static BigDecimal suma(List<BigDecimal> valores) {
        return valores.stream().filter(v -> v != null).reduce(CERO, BigDecimal::add).setScale(2);
    }

    /**
     * @param rs     fila
     * @param campo  columna de fecha y hora
     * @return fecha y hora ({@code null} si está vacía)
     * @throws SQLException si la columna no existe
     */
    private static LocalDateTime fecha(ResultSet rs, String campo) throws SQLException {
        Timestamp t = rs.getTimestamp(campo);
        return t == null ? null : t.toLocalDateTime();
    }
}
