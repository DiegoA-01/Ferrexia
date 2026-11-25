# Plan 029

Rama `feature/anular-factura-metodo-pago`, sacada de `develop` actualizado **después de la 022** (permisos). Migración
propuesta **V49**. Decisión **D16** debe estar tomada.

## Lo que hay hoy (revisado en el código)
- `POST /api/ventas/{id}/anular` (`AnulacionVentaService`, spec 006): solo dentro del mismo turno abierto. Rechaza con 409
  `VENTA_ANULADA`, `TURNO_CERRADO`, `TIENE_DEVOLUCIONES` y `EFECTIVO_INSUFICIENTE`. Pide autorización de supervisor
  (`AccionAutorizada.ANULAR_VENTA`), devuelve el stock y deja un movimiento `ANULACION` en la caja.
- `Venta` guarda `anuladaEn`, `anuladaPorUsuarioId`, `motivoAnulacion` y `anulacionAutorizadaPorUsuarioId`.
- Medios de la venta (`MedioPagoVenta`): EFECTIVO, TARJETA_DEBITO, TARJETA_CREDITO, TRANSFERENCIA, BILLETERA, CREDITO y
  SALDO_FAVOR, guardados en `venta_pago`. La caja (`movimiento_caja`) tiene un `VENTA` por medio.
- No existe nada para cambiar el medio de una venta ya cobrada.

## Backend
1. **Anular fuera del turno:** `AnulacionVentaService.anular` acepta una venta de un turno cerrado **solo** si quien anula
   tiene `VENTA_ANULAR_OTRO_TURNO` y tiene un turno abierto. Entonces:
   - devuelve el stock como hoy;
   - registra el dinero de salida en **el turno abierto de quien anula** (movimiento `ANULACION` con referencia a la venta
     y al turno original), así el turno cerrado no cambia;
   - si la venta tenía CREDITO o SALDO_FAVOR, revierte la cuenta por cobrar o el saldo como en la devolución (spec 006/007);
   - sigue rechazando `TIENE_DEVOLUCIONES` y `EFECTIVO_INSUFICIENTE`;
   - el cuadre diario (spec 009) del día de la anulación lo muestra.
2. **Cambiar el medio de pago (D16), V49:**
   - Tabla `venta_pago_cambio (id, empresa_id, venta_id, turno_id, usuario_id, autorizado_por_usuario_id, antes JSON,
     despues JSON, motivo, creado_en)` con `@TenantId`. `venta_pago` no se edita: se marca la fila vieja como
     reemplazada (`venta_pago.reemplazado_por_cambio_id`) y se insertan las nuevas.
   - `POST /api/ventas/{id}/cambiar-pago` con los pagos nuevos y el motivo. Reglas: con permiso `VENTA_CAMBIAR_PAGO`; el
     total de los pagos nuevos = total de la venta; no se permite pasar a o desde CREDITO o SALDO_FAVOR (eso es
     cartera); no en ventas anuladas; no en facturas electrónicas (cuando existan).
   - **Caja:** en el turno abierto de quien cambia, un movimiento de ajuste por medio: − monto del medio viejo, + monto del
     medio nuevo (tipo nuevo `AJUSTE_PAGO` en `TipoMovimientoCaja`). Si la venta es del mismo turno, el esperado queda bien
     sin más; si es de un turno cerrado, el ajuste queda en el turno actual y en el cuadre del día.
   - Auditoría `VENTA_CAMBIAR_PAGO` con antes y después.
3. `VentaResponse` suma `cambiosDePago` (historial) y los pagos vigentes.

## Frontend
- **Ventas (historial) y Mi turno › Facturas del turno (spec 021):** por venta, **Anular** (ahora también de otros turnos
  para quien tenga el permiso) y **Cambiar método de pago** (diálogo con los medios de la 016, el total fijo, el motivo y,
  si no tiene permiso, el PIN del supervisor).
- El detalle de la venta muestra "Pago cambiado de Efectivo a Tarjeta débito por Ana, 24 nov 15:02, motivo…".
- El tiquete reimpreso usa los pagos vigentes y una línea "Pago corregido".

## Pruebas
- `AnulacionVentaServiceTest`: anular de otro turno con permiso deja el dinero en el turno abierto de quien anula; sin
  permiso 403; sin turno abierto 409; venta a crédito revierte la cuenta.
- Prueba del cambio de pago: suma distinta al total 400; a CREDITO 409; ajuste de caja correcto en el mismo turno y en otro
  turno; el turno cerrado no cambia; historial guardado.
- `CalculoCaja`: el ajuste de pago mueve el esperado de efectivo y de tarjeta.
- `AislamientoEmpresasTest`: B no anula ni cambia pagos de ventas de A (404).
- Frontend: diálogo de cambio de pago (total fijo, motivo obligatorio), anular de otro turno.

Verificación final: `./mvnw verify`, `npm test`, `npm run build`, e2e del CI y revisión en Docker con el cajero y el
supervisor (cambiar pago en el turno, anular una factura de ayer).
