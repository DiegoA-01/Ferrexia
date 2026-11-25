# Plan 023

Rama `feature/cierre-por-denominacion`, sacada de `develop` actualizado **después de la 021**. Migración propuesta
**V45**.

## Lo que hay hoy (revisado en el código)
- `CerrarTurnoRequest(contadoEfectivo, contadoOtros, observacion)`: el cajero escribe el total del efectivo a mano y, por
  medio, el total de vouchers y transferencias.
- `turno_caja` guarda `contado_efectivo`, `contado_otros` (JSON), `esperado_efectivo`, `esperado_otros` (JSON),
  `diferencia`, `diferencias` (JSON), `requiere_aprobacion` y los datos de aprobación. `CalculoCaja` (Java puro) calcula
  esperado y diferencias.
- Vistas existentes: `GET /api/turnos` (supervisor, admin, auditor; filtra por estado y pendientes) y el reporte de caja
  `GET /api/reportes/caja` (`CajaReporteService`, spec 009).

## Backend
1. **V45:** `turno_caja.conteo_denominaciones JSON NULL` (lista `[{ "tipo": "BILLETE", "valor": 50000, "cantidad": 3,
   "referencia": "" }]`). Se sigue el estilo de `contado_otros`.
2. **Denominaciones:** clase `Denominaciones` (Java puro) con los billetes (100.000, 50.000, 20.000, 10.000, 5.000, 2.000)
   y monedas (1.000, 500, 200, 100, 50) vigentes en Colombia. El de 1.000 existe como billete y como moneda: son dos filas.
3. **Cierre:** `CerrarTurnoRequest` suma `conteo` opcional (`List<ConteoDenominacion(tipo, valor, cantidad, referencia)>`).
   - Si viene, Java calcula el efectivo contado (Σ valor × cantidad) y **ese** es el `contadoEfectivo`; si además viene
     `contadoEfectivo` y no coincide, 400 `CONTEO_NO_CUADRA`.
   - Solo se aceptan denominaciones de la lista, cantidades enteras de 0 a 100.000 y una fila por denominación.
   - Si no viene, funciona como hoy (compatibilidad).
   - El resto del cierre (diferencias, tolerancia, aprobación) no cambia.
4. **Respuesta:** `TurnoResponse` suma `conteo` (lo guardado).
5. **Ventas por turno:** `GET /api/reportes/caja/turnos?desde&hasta&cajaId` (supervisor, admin, auditor): por turno, caja,
   cajero, apertura, cierre, ventas por medio de pago, devoluciones, ingresos, egresos, esperado, contado y diferencia. Se
   arma con consultas agregadas sobre `venta`, `venta_pago` y `movimiento_caja` (con `empresa_id`), y Java totaliza.

## Frontend
- **Cierre (componente `cierre-turno` de la 018):** tabla "Billetes" y "Monedas" con una fila por denominación, campo de
  cantidad (`scCifra` con 0 decimales), subtotal por fila y total del efectivo calculado en vivo; referencia por fila y nota
  general. Los otros medios (vouchers, transferencias) siguen como hoy.
- **Detalle del turno** (Cajas y turnos): muestra el conteo guardado.
- **Reportes › Ventas por turno:** tabla filtrable por fechas y caja, exportable a Excel como los demás reportes.

## Pruebas
- `DenominacionesTest` y prueba del cálculo: 3 × 50.000 + 2 × 1.000 (billete) + 4 × 1.000 (moneda) = 156.000.
- `TurnoServiceTest`: cerrar con conteo guarda el conteo y el contado calculado; conteo que no cuadra con `contadoEfectivo`
  da 400; denominación inventada da 400; sin conteo funciona igual que antes.
- `CajaReporteServiceTest`: ventas por turno suman igual que el cuadre de la spec 009.
- `AislamientoEmpresasTest`: el reporte de turnos de A no trae turnos de B.
- Frontend: la tabla suma en vivo y envía el conteo; detalle del turno con el conteo.

Verificación final: `./mvnw verify`, `npm test`, `npm run build`, e2e del CI y revisión en Docker con el cajero (cerrar
contando) y el supervisor (ver el reporte).
