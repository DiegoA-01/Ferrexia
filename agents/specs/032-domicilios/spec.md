# 032 - Módulo de domicilios

**Estado:** Hecha (2026-11-26; falta revisión en Docker) · **Rama:** `feature/domicilios` · **Depende de:** 004, 021 · **Responsable:** Persona 1 ·
**Puntos de la reunión 2:** 6 · **Migración:** V53 · **Decisión:** D19 (tomada)

## Objetivo
Que el negocio venda a domicilio desde el sistema: tomar el pedido con la dirección, asignar un domiciliario, seguir el
estado y cuadrar el dinero que llega en la entrega.

## Decisiones (2026-11-26)
- **D19, tal como está:** venta tipo domicilio con dirección, teléfono, referencia y costo del envío; domiciliario
  interno (con permiso) o externo (nombre y teléfono); estados con historial; pago antes o contraentrega; tablero del
  día. Sin mapa en vivo ni app.
- **Contraentrega = venta al despachar:** la venta se confirma cuando sale el domicilio, así el stock baja cuando sale la
  mercancía. El pago queda como "Contraentrega" (no entra a la caja). Cuando vuelve el domiciliario, un cajero lo cobra
  en su turno con el medio real. Si vuelve devuelto, la venta se anula y el stock regresa. No hay reservas de stock.
- **Envío = producto servicio:** cada empresa tiene un producto "Envío a domicilio" marcado como servicio (sin stock ni
  kardex) que se suma como línea de la venta; su precio se fija como cualquier otro en la lista General. Los productos
  ganan la marca "es servicio".
- Domicilios va dentro del módulo de Ventas (no es un módulo aparte).

## Historias
- Como vendedor, quiero registrar una venta a domicilio con dirección, teléfono y costo del envío.
- Como supervisor, quiero asignar el domicilio a un domiciliario y ver en qué va.
- Como cajero, quiero recibir el dinero del domicilio contraentrega en mi turno.
- Como dueño, quiero saber cuánto se vende a domicilio.

## Comportamiento
- **Venta a domicilio:** en el cobro del punto de venta se activa "Domicilio" y se escriben dirección (se propone la del
  cliente), teléfono y referencia; si la empresa configuró el envío, su línea se agrega al carrito. El pago puede ser
  normal (pagado antes) o "Contraentrega".
- **Estados:** Pendiente → Asignado → En camino → Entregado, o Devuelto (desde En camino) / Cancelado (antes de salir),
  con motivo. Cada cambio guarda quién, cuándo y la nota.
- **Domiciliarios:** trabajadores con el permiso "repartir domicilios" (ven solo los suyos y los marcan en camino y
  entregado), o externos con nombre y teléfono.
- **Dinero:** si se pagó antes, entró a la caja al cobrar; si es contraentrega, entra al turno del cajero que lo recibe
  al volver el domiciliario (movimiento "cobro de domicilio"). Devuelto o cancelado anula la venta y devuelve el stock
  (y el dinero, si se había pagado, sale del turno de quien lo devuelve).
- **Tablero de domicilios:** lista del día por estado, con tiempos, asignar, cambiar estado y cobrar contraentrega.
- La venta guarda su **tipo de servicio** (mostrador o domicilio) para los reportes de la spec 034.

## Criterios de aceptación
- [x] Se registra una venta a domicilio con dirección y costo del envío (el envío es un servicio: suma a la venta y no
  mueve stock).
- [x] El domicilio pasa por sus estados y cada cambio queda registrado (historial con quién, cuándo y nota; las
  transiciones fuera de orden dan 409).
- [x] El dinero contraentrega entra al turno correcto y la caja cuadra (`COBRO_DOMICILIO` en el turno de quien cobra;
  la venta queda con el pago real).
- [x] Un domicilio devuelto devuelve el stock (y uno cancelado antes de salir, también).
- [x] Prueba de fuga entre empresas para domicilios.

## Fuera de alcance
- Seguimiento en mapa en tiempo real y app para el domiciliario.
- Pedidos desde una tienda en línea o WhatsApp automático.
- Costo de envío por zona o por distancia (hay un solo precio de envío por lista).
