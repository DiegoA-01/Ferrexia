# 029 - Anular una factura y cambiar su método de pago

**Estado:** Hecha (2026-11-26; falta revisión en Docker) · **Rama:** `feature/anular-factura-metodo-pago` · **Depende de:** 006, 022 ·
**Responsable:** Persona 1 · **Puntos de la reunión 2:** 21, 43 · **Migración:** V51 · **Decisión:** D16 (tomada)

## Objetivo
Corregir errores de cobro sin descuadrar la caja: anular una factura también fuera del turno en que se hizo, y cambiar
el método de pago cuando el cajero se equivocó (por ejemplo, marcó efectivo y era tarjeta).

## Decisiones (2026-11-26)
- **D16, tal como está:** el método de pago de una factura POS se puede cambiar con permiso y motivo; el total no
  cambia; nunca en facturas electrónicas DIAN (cuando existan). No se pasa a ni desde crédito o saldo a favor (eso es
  cartera).
- **Permisos nuevos** `VENTA_ANULAR_OTRO_TURNO` y `VENTA_CAMBIAR_PAGO` para Dueño, Administrador y Supervisor de ventas.
  El cajero puede cambiar el pago de una factura de **su turno abierto** con el PIN del supervisor, como la anulación de
  hoy. Sin el permiso, una factura de un turno cerrado no se toca.

## Historias
- Como supervisor, quiero anular una factura de un turno ya cerrado, con motivo.
- Como cajero, quiero cambiar el método de pago de una factura de mi turno si me equivoqué.

## Comportamiento
- **Anular:** hoy se anula dentro del turno (spec 006). Se suma la anulación de una factura de un turno cerrado para
  quien tenga el permiso "anular factura de otro turno" y un turno abierto: devuelve el stock, registra la salida del
  dinero en el turno abierto de quien anula (el turno cerrado no cambia), revierte la cartera si la hubo y queda con
  motivo en la auditoría. La factura anulada sigue visible con su estado.
- **Cambiar método de pago (D16):** en una factura POS se cambian los pagos por otros que suman exactamente lo mismo,
  con motivo. La diferencia por medio queda en la caja como un movimiento "ajuste de pago": en el turno de la factura si
  sigue abierto, o en el turno abierto de quien cambia si la factura es de un turno cerrado (y así sale en el reporte de
  ventas por turno del día). Si el ajuste saca efectivo, la caja debe tenerlo.
- La factura guarda sus pagos vigentes y el historial de cambios (antes, después, quién, quién autorizó, cuándo y por
  qué); el detalle y el tiquete reimpreso muestran los pagos vigentes y la marca "Pago corregido".
- El total de la factura nunca cambia con el cambio de método de pago. Una factura anulada o con devoluciones no cambia
  su pago.

## Criterios de aceptación
- [x] Con permiso se anula una factura de un turno cerrado, con motivo, y el stock vuelve (el dinero sale del turno
  de quien anula; el turno cerrado no cambia).
- [x] Con permiso se cambia el método de pago de una factura POS y la caja cuadra (ajuste por medio; el total y el
  esperado del turno no cambian).
- [x] Sin permiso, las dos acciones se rechazan (403; el cajero solo con el PIN del supervisor y en su turno abierto).
- [x] Las dos acciones quedan en la auditoría (`VENTA_ANULAR` y `VENTA_CAMBIAR_PAGO`).

## Fuera de alcance
- Notas crédito y factura electrónica DIAN.
- Cambiar productos o precios de una factura ya cobrada (para eso está la devolución).
