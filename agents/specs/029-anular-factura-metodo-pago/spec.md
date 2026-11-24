# 029 - Anular una factura y cambiar su método de pago

**Estado:** Propuesta · **Rama:** `feature/anular-factura-metodo-pago` · **Depende de:** 006, 022 · **Responsable:**
Persona 1 · **Puntos de la reunión 2:** 21, 43 · **Migración:** posible (se reserva al empezar) · **Decisión:** D16

## Objetivo
Corregir errores de cobro sin descuadrar la caja: anular una factura también fuera del turno en que se hizo, y cambiar
el método de pago cuando el cajero se equivocó (por ejemplo, marcó efectivo y era tarjeta).

## Historias
- Como supervisor, quiero anular una factura de un turno ya cerrado, con motivo.
- Como cajero, quiero cambiar el método de pago de una factura de mi turno si me equivoqué.

## Comportamiento
- **Anular:** hoy se anula dentro del turno (spec 006). Se suma la anulación de una factura de un turno cerrado para
  quien tenga el permiso "anular factura de otro turno": devuelve el stock, registra el movimiento de dinero en el turno
  abierto de quien anula y queda con motivo en la auditoría. La factura anulada sigue visible con su estado.
- **Cambiar método de pago (D16):** en una factura POS se puede cambiar el método de pago, con permiso y motivo. Si la
  factura es del turno abierto, el esperado de la caja se ajusta; si es de un turno cerrado, el cambio queda como un
  ajuste en el turno abierto y en el cuadre del día. Cuando exista la factura electrónica DIAN, este cambio quedará
  bloqueado para ellas.
- El total de la factura nunca cambia con el cambio de método de pago.

## Criterios de aceptación
- [ ] Con permiso se anula una factura de un turno cerrado, con motivo, y el stock vuelve.
- [ ] Con permiso se cambia el método de pago de una factura POS y la caja cuadra.
- [ ] Sin permiso, las dos acciones se rechazan.
- [ ] Las dos acciones quedan en la auditoría.

## Fuera de alcance
- Notas crédito y factura electrónica DIAN.
- Cambiar productos o precios de una factura ya cobrada (para eso está la devolución).
