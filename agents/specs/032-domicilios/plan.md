# Plan 032

Rama `feature/domicilios`, sacada de `develop` actualizado **después de la 021**. Migración propuesta **V52**. Decisión
**D19** debe estar tomada.

## Lo que hay hoy (revisado en el código)
- **Venta** (`Venta`): caja, turno, bodega, cliente, vendedor, cajero, lista, pedido, totales, crédito e idempotencia. No
  tiene tipo de servicio ni dirección. Se confirma ya cobrada: los pagos deben cuadrar al confirmar.
- **Pedido de venta** (`PedidoVenta`): cliente, vendedor, nota, `ventaId` y estado PENDIENTE_COBRO, COBRADO o DESCARTADO
  (con motivo, spec 016). No mueve stock ni caja hasta que se cobra.
- **Cliente** (spec 002): documento, nombre, teléfono y dirección (opcional).
- **Módulos:** no hay uno de domicilios.

## Diseño
El domicilio se apoya en lo que ya existe:
- **Pagado antes de salir:** se cobra como venta normal y se le asocia un domicilio.
- **Contraentrega:** se arma como **pedido de venta** con domicilio; el stock y el dinero se mueven **cuando se cobra**, es
  decir, cuando el domiciliario vuelve y el cajero lo cobra en su turno. Así no hay ventas "medio cobradas".
- Decisión a confirmar en D19: si en contraentrega la mercancía debe **apartarse** al salir (para no venderla dos veces).
  Propuesta: sí, con una reserva simple (ver punto 4).

## Backend
1. **Módulo `DOMICILIOS`** en `Modulos` (activo para todos, spec 024).
2. **V52:**
   - `venta.tipo_servicio ENUM('MOSTRADOR','DOMICILIO') NOT NULL DEFAULT 'MOSTRADOR'` y lo mismo en `pedido_venta`.
   - `domicilio (id, empresa_id, consecutivo, venta_id NULL, pedido_venta_id NULL, cliente_id, direccion, telefono,
     referencia, costo_envio, pago ENUM('PAGADO','CONTRAENTREGA'), estado ENUM('PENDIENTE','ASIGNADO','EN_CAMINO',
     'ENTREGADO','DEVUELTO','CANCELADO'), domiciliario_usuario_id NULL, domiciliario_externo_nombre NULL,
     domiciliario_externo_telefono NULL, motivo, creado_en, entregado_en)` con CHECK: venta o pedido, uno de los dos.
   - `domicilio_evento (domicilio_id, estado, usuario_id, nota, creado_en)` para el historial.
   - Consecutivo `DOM` por empresa; `empresa.costo_envio_defecto DECIMAL NULL`; eventos de notificación por empresa.
3. **Servicio `DomicilioService`** (`/api/domicilios`): crear desde una venta pagada o desde un pedido; asignar
   domiciliario; cambiar estado con transiciones válidas; listar por día y estado; detalle con historial.
   - El costo de envío se agrega como línea de servicio (producto de tipo servicio sin stock, creado por empresa) para que
     sume en la venta y en los reportes sin tocar el cálculo de impuestos.
   - **Contraentrega:** al pasar a ENTREGADO queda listo para cobrar; el cajero lo cobra desde "Pedidos de venta" (flujo de
     la spec 005), y el turno recibe el dinero. Si vuelve DEVUELTO, el pedido se descarta con motivo.
4. **Reserva (si D19 la aprueba):** tabla `reserva_stock (producto_id, bodega_id, cantidad, domicilio_id)`; la búsqueda para
   vender resta lo reservado del disponible. La reserva se libera al cobrar (la salida real la hace la venta) o al
   devolver. No toca `stock_producto_bodega`.
5. **Domiciliario:** permiso `DOMICILIO_REPARTIR` (spec 022) para ver solo sus domicilios y marcar EN_CAMINO y ENTREGADO.
6. `VentaResumen` y los reportes de la 034 llevan `tipoServicio`.

## Frontend
- **POS y pedido de venta:** interruptor "Domicilio" que pide dirección (prellenada del cliente), teléfono, referencia,
  costo del envío y si es pagado o contraentrega.
- **Ventas › Domicilios:** tablero del día por estado (columnas Pendiente, Asignado, En camino, Entregado/Devuelto) con
  tiempos, asignar domiciliario y cambiar estado.
- **Vista del domiciliario:** lista de sus domicilios con dirección, teléfono (enlace `tel:`), "Abrir en mapa" y botones de
  estado; pensada para celular.

## Pruebas
- `DomicilioServiceTest`: transiciones válidas e inválidas; pagado vs contraentrega; devuelto descarta el pedido; costo de
  envío como línea.
- Reserva (si aplica): lo reservado no se puede vender; se libera al cobrar o devolver.
- Cobro de contraentrega entra al turno del cajero que cobra y la caja cuadra.
- `AislamientoEmpresasTest`: domicilios y eventos de A no se ven en B; un domiciliario de B no ve los de A.
- Frontend: interruptor en el POS, tablero, vista del domiciliario.
- E2E: venta a domicilio contraentrega de punta a punta.

Verificación final: `./mvnw verify`, `npm test`, `npm run build`, e2e y revisión en Docker con vendedor, cajero y
domiciliario a 360 px.
