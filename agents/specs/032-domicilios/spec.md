# 032 - Módulo de domicilios

**Estado:** Propuesta · **Rama:** `feature/domicilios` · **Depende de:** 004, 021 · **Responsable:** Persona 1 ·
**Puntos de la reunión 2:** 6 · **Migración:** sí (se reserva al empezar) · **Decisión:** D19

## Objetivo
Que el negocio venda a domicilio desde el sistema: tomar el pedido con la dirección, asignar un domiciliario, seguir el
estado y cuadrar el dinero que llega en la entrega.

## Historias
- Como vendedor, quiero registrar una venta a domicilio con dirección, teléfono y costo del envío.
- Como supervisor, quiero asignar el domicilio a un domiciliario y ver en qué va.
- Como cajero, quiero recibir el dinero del domicilio contraentrega en mi turno.
- Como dueño, quiero saber cuánto se vende a domicilio.

## Comportamiento
- **Venta tipo domicilio (D19):** en el pedido o en el POS se elige "Domicilio" y se piden cliente, dirección,
  teléfono, referencia y costo del envío (opcional, configurable por empresa).
- **Estados:** Pendiente → Asignado → En camino → Entregado, o Devuelto/Cancelado con motivo. Cada cambio guarda quién y
  cuándo.
- **Domiciliarios:** trabajadores con el permiso "domiciliario", o domiciliarios externos con nombre y teléfono.
- **Dinero:** si se pagó antes, entra a la caja al cobrar; si es contraentrega, queda pendiente y entra al turno del
  cajero que lo recibe al volver el domiciliario. Un domicilio devuelto devuelve el stock.
- **Tablero de domicilios:** lista del día por estado, con tiempos.
- La venta guarda su **tipo de servicio** (mostrador o domicilio) para los reportes de la spec 034.

## Criterios de aceptación
- [ ] Se registra una venta a domicilio con dirección y costo del envío.
- [ ] El domicilio pasa por sus estados y cada cambio queda registrado.
- [ ] El dinero contraentrega entra al turno correcto y la caja cuadra.
- [ ] Un domicilio devuelto devuelve el stock.
- [ ] Prueba de fuga entre empresas para domicilios.

## Fuera de alcance
- Seguimiento en mapa en tiempo real y app para el domiciliario.
- Pedidos desde una tienda en línea o WhatsApp automático.
