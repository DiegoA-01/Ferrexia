# Plan 029

Rama `feature/anular-factura-metodo-pago`, sacada de `develop` actualizado (`732d2b3`, con la 022 y la 028 mezcladas).
Migración **V51** (reservada en el índice). D16 tomada el 2026-11-26.

## Lo que hay hoy (revisado en el código)
- `POST /api/ventas/{id}/anular` (`AnulacionVentaService`, spec 006, permiso `VENTA_COBRAR`): solo el cajero del turno
  abierto, con PIN del supervisor (`AccionAutorizada.ANULAR_VENTA`). Revierte en la caja los movimientos `VENTA` de la
  venta, devuelve el stock y revierte la cartera.
- `venta_pago` (sin `empresa_id`, hereda la de la venta) guarda los pagos; la caja tiene un `VENTA` por medio que entra.
- `movimiento_caja.tipo` y `autorizacion_supervisor.accion` son ENUM de MySQL: un valor nuevo exige migración.
- El reporte de ventas por turno (`CajaReporteService.ventasPorTurno`) clasifica el libro por tipo.

## Backend
1. **V51:**
   - `movimiento_caja.tipo` suma `AJUSTE_PAGO`; `autorizacion_supervisor.accion` suma `CAMBIAR_PAGO`.
   - Tabla `venta_pago_cambio (id, empresa_id, venta_id, turno_id, usuario_id, autorizado_por_usuario_id, antes JSON,
     despues JSON, motivo, creado_en)` con `@TenantId`.
   - Permisos `VENTA_ANULAR_OTRO_TURNO` y `VENTA_CAMBIAR_PAGO` (módulo VENTAS) para ADMIN_SISTEMA, ADMINISTRADOR y
     SUPERVISOR_VENTAS (plantillas). La prueba de permisos de migraciones lee también la V51.
2. **Anular de otro turno:** `POST /api/ventas/{id}/anular-otro-turno` (`VENTA_ANULAR_OTRO_TURNO`, módulo CAJA) con el
   motivo. Reglas: venta confirmada (409 `VENTA_ANULADA`), de un turno cerrado (si sigue abierto, 409 `TURNO_ABIERTO`:
   se anula desde ese turno), sin devoluciones (409), quien anula tiene un turno abierto (409 `SIN_TURNO_ABIERTO`) con el
   efectivo suficiente (409 `EFECTIVO_INSUFICIENTE`). La salida del dinero va al turno de quien anula.
   **Las dos anulaciones revierten el neto por medio de los movimientos `VENTA` y `AJUSTE_PAGO` de la venta**, para que
   una venta con el pago cambiado se anule con sus pagos vigentes.
3. **Cambiar el pago:** `POST /api/ventas/{id}/cambiar-pago` (permiso de entrada `VENTA_VER`, módulo CAJA) con los
   pagos nuevos (`PagoVentaRequest`), el motivo y la autorización opcional. Reglas:
   - con `VENTA_CAMBIAR_PAGO`, sin PIN; sin él, solo el cajero del turno abierto de la venta y con el PIN del supervisor
     (`CAMBIAR_PAGO`); si no, 403;
   - venta confirmada, sin devoluciones, sin crédito ni saldo a favor en sus pagos ni en los nuevos (409
     `PAGO_DE_CARTERA`); los nuevos suman exactamente el total a pagar (409 `PAGOS_NO_CUADRAN`) y cambian algo (409
     `SIN_CAMBIOS`);
   - turno del ajuste: el de la venta si está abierto; si no, el turno abierto de quien cambia (409 `SIN_TURNO_ABIERTO`);
   - caja: un `AJUSTE_PAGO` por medio con la diferencia (nuevo − viejo) distinta de cero; si saca efectivo, el turno debe
     tenerlo (409 `EFECTIVO_INSUFICIENTE`);
   - `venta_pago` queda con los pagos vigentes (las filas viejas se borran y se insertan las nuevas); el antes y el
     después quedan en `venta_pago_cambio` y en la auditoría `VENTA_CAMBIAR_PAGO`.
4. `VentaResponse` suma `cambiosPago` (historial). El tiquete reimpreso lleva "Pago corregido" si hubo cambios.
5. El reporte de ventas por turno suma `AJUSTE_PAGO` a las ventas de su medio (el total del turno no cambia).

## Frontend
- **Facturas del turno (Mi turno) y detalle de la venta:** "Cambiar método de pago" (diálogo con los medios de caja, el
  total fijo, el motivo y, si no tiene el permiso, el PIN del supervisor) y, para quien tenga el permiso, "Anular" de
  una factura de un turno cerrado con motivo.
- El detalle de la venta muestra el historial: "Pago cambiado de Efectivo a Tarjeta débito por Ana, 24 nov 15:02".

## Pruebas
- `CambioPagoVentaServiceTest`: cambio con permiso en el mismo turno (ajustes por medio, pagos vigentes, historial);
  factura de un turno cerrado (ajuste en el turno de quien cambia); suma distinta 409; crédito 409; sin cambios 409;
  sin permiso y sin PIN 403; cajero con PIN; efectivo insuficiente 409.
- `AnulacionVentaServiceTest` (o nueva): anular de otro turno deja el dinero en el turno de quien anula; turno abierto
  409; sin turno abierto 409; anular después de un cambio de pago revierte los pagos vigentes.
- `CajaReporteServiceTest`: el ajuste de pago mueve las ventas por medio sin cambiar el total.
- `AislamientoEmpresasTest`: B no anula ni cambia pagos de ventas de A (404) y el historial es de su empresa.
- Frontend: diálogo de cambio de pago (total fijo, motivo obligatorio, PIN sin permiso) y anular de otro turno.

Verificación final: `./mvnw verify`, `npm test`, tipos regenerados, e2e del CI y revisión en Docker con el cajero y el
supervisor (cambiar pago en el turno, anular una factura de ayer).
