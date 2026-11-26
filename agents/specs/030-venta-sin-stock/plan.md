# Plan 030

Rama `feature/venta-sin-stock`, sacada de `develop` actualizado (`c41d935`, con la 029). Migración **V52** (reservada en el
índice). D13 tomada el 2026-11-26: faltante pendiente.

## Lo que hay hoy (revisado en el código)
- `VentaService.confirmar` descuenta el stock con una salida por producto, en orden de id, por
  `MovimientoInventarioService.registrarSalidaVenta`; si alguno no alcanza, `STOCK_INSUFICIENTE` (409) deshace la venta.
- `MovimientoInventarioService.aplicar` es el único que cambia el stock: bloquea la fila de stock y nunca la deja
  negativa.
- La anulación (spec 006 y 029) devuelve al stock lo vendido por producto; la devolución mete lo devuelto.

## Diseño (D13: faltante pendiente; el stock nunca baja de cero)
1. **V52:** tabla `venta_sin_existencia (id, empresa_id, venta_id, producto_id, bodega_id, usuario_id, cantidad,
   pendiente, motivo, estado ENUM('PENDIENTE','REGULARIZADA','ANULADA'), creado_en, regularizada_en)` con `@TenantId`;
   permiso `VENTA_SIN_EXISTENCIA` (Dueño, Administrador, Supervisor de ventas); evento `VENTA_SIN_EXISTENCIA` en
   `config_notificacion` de las empresas que ya existen.
2. **Venta:** `LineaVentaRequest` suma `sinExistencia` y `motivoSinExistencia`. Si un producto tiene alguna línea
   marcada: sin el permiso, 403; con él, `MovimientoInventarioService.registrarSalidaVentaConFaltante` descuenta
   `min(disponible, cantidad)` y registra la diferencia como faltante PENDIENTE, y avisa a compras y bodega. Una línea
   marcada sin motivo da 400. Una línea sin marcar sigue con 409 `STOCK_INSUFICIENTE`.
3. **Regularización:** al final de toda entrada (`aplicar` con bodega de destino), los faltantes pendientes de ese
   producto en esa bodega, del más viejo al más nuevo, se descuentan con una `SALIDA_VENTA` que apunta a su venta, hasta
   donde alcance el stock. Todo en la misma transacción y con la fila de stock bloqueada: nunca queda negativo.
4. **Anulación:** antes de devolver el stock se anulan los faltantes pendientes de la venta y solo vuelve lo que salió
   (vendido − pendiente). **Devolución:** lo devuelto de un producto primero descuenta el pendiente de esa venta y solo el
   resto entra al stock.
5. **Reporte:** `GET /api/reportes/ventas-sin-existencia?desde&hasta&productoId&estado` (permiso `REPORTE_VENTAS`).
6. **Respuesta de la venta:** cada línea dice si tuvo faltante (`sinExistencia`) para mostrarlo en el detalle.

## Frontend
- **POS:** si al cobrar el backend responde `STOCK_INSUFICIENTE` y la sesión tiene el permiso, aparece "No hay existencia
  suficiente de X. ¿Vender igual?" con el motivo; al confirmar, se reenvía con las líneas de ese producto marcadas. Sin el
  permiso, el error de siempre.
- **Reportes › Ventas sin existencia** con filtros de fecha y estado.

## Pruebas
- Prueba de punta a punta contra MySQL (`AislamientoEmpresasTest`): sin permiso 403, sin marcar 409, con permiso vende
  y crea el faltante por la diferencia; una entrada lo regulariza parcial y luego total sin dejar el stock negativo; la
  anulación anula lo pendiente y devuelve solo lo que salió; el aviso llega a compras; B no ve el faltante ni el reporte.
- `MovimientoInventarioServiceTest` (o nueva) para la regularización en orden.
- Frontend: diálogo del POS con motivo y reporte.

Verificación final: `./mvnw verify`, `npm test`, tipos, e2e y revisión en Docker (vender sin existencia, recibir la
mercancía y ver el faltante regularizado).
