# 016 - Ajustes rápidos del Punto de venta

**Estado:** Propuesta · **Rama:** `feature/pos-ajustes-rapidos` · **Depende de:** 005, 006 · **Responsable:** Persona 1
· **Puntos de la reunión 2:** 20, 22, 24, 48

## Objetivo
Quitar fricciones que se vieron al cobrar en el Punto de venta: datos de tarjeta que el cajero no necesita escribir, un
voucher que hoy frena el cobro y un método de pago que se puede dejar sin elegir. Y que en Ventas no "desaparezcan" los
pedidos que se eliminan.

## Historias
- Como cajero, quiero cobrar con tarjeta sin escribir los últimos 4 dígitos, para atender más rápido.
- Como cajero, quiero que el código del voucher sea opcional, porque no siempre lo tengo a mano.
- Como supervisor, quiero que ninguna venta quede sin método de pago.
- Como supervisor, quiero ver los pedidos de venta eliminados, para saber qué se dejó de vender y quién lo eliminó.

## Comportamiento
- **Tarjeta:** el cobro con tarjeta ya no pide los últimos 4 dígitos. Las ventas anteriores conservan el dato que
  tengan.
- **Voucher:** el código del voucher (número de autorización) es opcional en el cobro con tarjeta.
- **Método de pago obligatorio:** no se puede confirmar el cobro sin elegir al menos un método de pago; el botón queda
  desactivado y sale el mensaje "Elige cómo paga el cliente". El backend también lo rechaza.
- **Pedidos eliminados:** la lista de pedidos de venta tiene un filtro "Eliminados" que muestra los pedidos eliminados
  o cancelados con quién lo hizo, cuándo y el motivo. No se pueden volver a cobrar desde ahí.

## Criterios de aceptación
- [ ] El cobro con tarjeta no pide los últimos 4 dígitos.
- [ ] El código del voucher es opcional y la venta se guarda sin él.
- [ ] No se puede confirmar una venta sin método de pago, ni desde la pantalla ni llamando a la API.
- [ ] La lista de pedidos de venta muestra los eliminados con quién, cuándo y por qué.
- [ ] Las ventas hechas antes de este cambio se siguen viendo igual.

## Fuera de alcance
- Juntar cliente y método de pago en un solo paso (spec 027).
- Cambiar el método de pago de una venta ya cobrada (spec 029).
