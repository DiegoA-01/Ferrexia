# Plan 030

Rama `feature/venta-sin-stock`, sacada de `develop` actualizado **después de la 022**. Migración propuesta **V50**.
Decisión **D13** debe estar tomada (qué pasa con el stock).

## Lo que hay hoy (revisado en el código)
- `VentaService` descuenta el stock con una salida por producto, en orden de id, a través de
  `MovimientoInventarioService`; si alguno no alcanza, `STOCK_INSUFICIENTE` (409) deshace toda la venta.
- `MovimientoInventarioService` es el único que cambia el stock y **nunca** lo deja negativo (regla que no se negocia en
  `CLAUDE.md`, probada con 20 salidas concurrentes).
- Decisión D1 de ventas: no vender sin stock.

## Opción recomendada para D13: faltante pendiente (el stock nunca baja de cero)
Así no se rompe la regla del stock. La alternativa (stock negativo marcado) obliga a cambiar
`MovimientoInventarioService`, el kardex, el costo promedio y la reorden, y debilita una garantía que hoy está probada.

## Backend
1. **V50:** tabla `venta_sin_existencia (id, empresa_id, venta_id, venta_linea_id, producto_id, bodega_id, cantidad,
   motivo, estado ENUM('PENDIENTE','REGULARIZADA','ANULADA'), regularizada_en, regularizada_por_movimiento_id, creado_en)`
   con `@TenantId`. Evento `VENTA_SIN_EXISTENCIA` sembrado por empresa.
2. **Venta:** `LineaVentaRequest` suma `sinExistencia` (bool) y `motivoSinExistencia`. En `VentaService`, para una línea
   marcada:
   - exige el permiso `VENTA_SIN_EXISTENCIA` (si no, 403);
   - descuenta lo que haya (`min(disponible, cantidad)`) con la salida normal y registra la diferencia como
     `venta_sin_existencia` PENDIENTE;
   - el costo de la parte faltante usa el costo promedio vigente (spec 008) y se marca como estimado.
   Una línea **no** marcada sigue rechazando con `STOCK_INSUFICIENTE` como hoy.
3. **Regularización:** cuando entra mercancía de ese producto a esa bodega (recepción, ajuste de entrada o traslado de
   entrada), `MovimientoInventarioService` llama a `RegularizacionService`, que descuenta los faltantes pendientes más
   viejos primero con salidas `SALIDA_VENTA` que apuntan a la venta original, en la misma transacción. Así el stock nunca
   queda negativo y el kardex cuenta la verdad.
4. **Anulación o devolución** de una venta con faltante pendiente: el faltante pasa a ANULADA (no se devuelve stock que
   nunca salió).
5. **Aviso:** al crear un faltante, notificación a compras y a bodega (`VENTA_SIN_EXISTENCIA`).
6. **Reporte:** `GET /api/reportes/ventas-sin-existencia?desde&hasta&productoId&estado` (supervisor, admin, compras,
   auditor) con producto, cantidad, cajero, caja, motivo, estado y fecha de regularización.

## Frontend
- **POS:** si un producto no alcanza, en vez de solo el error, aparece "No hay existencia suficiente (hay 2). ¿Vender igual?"
  con el motivo ("No registrado", "Se consigue afuera", "Otro") solo para quien tenga el permiso.
- **Reportes › Ventas sin existencia** con filtros y estado.

## Pruebas
- `VentaServiceTest`: línea marcada con permiso vende y crea el faltante por la diferencia; sin permiso 403; sin marcar
  sigue 409.
- `RegularizacionServiceTest`: una recepción regulariza los faltantes en orden, parcial y total; el stock nunca queda
  negativo; concurrencia de dos recepciones.
- Anulación con faltante pendiente lo marca ANULADA.
- `AislamientoEmpresasTest`: faltantes y reporte de A no se ven en B.
- Frontend: aviso en el POS con motivo; reporte.

Verificación final: `./mvnw verify`, `npm test`, `npm run build`, e2e y revisión en Docker (vender sin existencia, recibir
la mercancía y ver el faltante regularizado).
