# Plan 032

Rama `feature/domicilios`, sacada de `develop` actualizado (`fe6e60b`, con la 030). Migración **V53** (reservada en el
índice). D19 tomada el 2026-11-26: contraentrega como venta al despachar y envío como producto servicio.

## Lo que hay hoy (revisado en el código)
- `Venta` se confirma ya cobrada: los pagos cuadran al confirmar; el crédito y el saldo a favor no entran a la caja.
- La caja (`movimiento_caja.tipo`) y los medios de la venta (`venta_pago.medio`) son ENUM de MySQL.
- La anulación (spec 006 y 029) revierte el neto de la venta en la caja, el stock y la cartera.
- El cuadre diario (spec 009) compara, por venta, lo cobrado por medios de caja con la caja y lo vendido con las salidas.

## Diseño
1. **V53:**
   - `producto.es_servicio`; `venta.tipo_servicio ENUM('MOSTRADOR','DOMICILIO')`.
   - `venta_pago.medio` suma `CONTRAENTREGA`; `movimiento_caja.tipo` suma `COBRO_DOMICILIO`.
   - `domicilio (id, empresa_id, consecutivo, venta_id, direccion, telefono, referencia, pago ENUM('PAGADO',
     'CONTRAENTREGA'), estado ENUM('PENDIENTE','ASIGNADO','EN_CAMINO','ENTREGADO','DEVUELTO','CANCELADO'),
     domiciliario_usuario_id, domiciliario_nombre, domiciliario_telefono, motivo, creado_en, entregado_en, cobrado_en,
     cobrado_turno_id, cobrado_por_usuario_id)` y `domicilio_evento (domicilio_id, estado, usuario_id, nota, creado_en)`.
   - Permisos `DOMICILIO_GESTIONAR` (Dueño, Administrador, Supervisor de ventas) y `DOMICILIO_REPARTIR` (sin plantilla:
     se da como permiso suelto o en un rol propio "Domiciliario").
2. **Producto servicio:** no mueve stock. La venta, la anulación y la devolución saltan sus líneas;
   `MovimientoInventarioService` rechaza un servicio (409 `PRODUCTO_SERVICIO`); los selectores de compras e inventario y
   el reporte de inventario y rotación lo dejan fuera.
3. **Venta a domicilio:** `VentaRequest.domicilio` (dirección, teléfono, referencia). `CONTRAENTREGA` es un medio que no
   entra a la caja, debe ser el único pago, por el total exacto, y exige domicilio. Al confirmar se crea el domicilio
   PENDIENTE con su consecutivo `DOM` y la venta queda `DOMICILIO`.
4. **`DomicilioService`** (`/api/domicilios`):
   - listar por día y estado (quien gestiona o cobra ve todos; el domiciliario, los suyos); detalle con historial;
   - asignar (interno con el permiso o externo) — `DOMICILIO_GESTIONAR`;
   - estados: EN_CAMINO y ENTREGADO (quien gestiona o el domiciliario asignado); DEVUELTO desde EN_CAMINO y CANCELADO
     antes de salir (quien gestiona), que anulan la venta sin PIN (si se pagó antes, el dinero sale del turno abierto de
     quien lo hace);
   - cobrar contraentrega (`VENTA_COBRAR` con turno abierto): pagos por medios de caja que suman el total; un
     `COBRO_DOMICILIO` por medio en ese turno y los pagos de la venta pasan a ser los reales;
   - envío: `GET /api/domicilios/envio` (producto y precio, si existe) y `PUT /api/domicilios/envio` (precio; crea el
     producto servicio la primera vez, exige poder fijar precios);
   - domiciliarios: usuarios activos con el permiso de repartir.
5. **Anulación:** el neto de la venta suma `COBRO_DOMICILIO`; anular la venta de un domicilio abierto lo deja CANCELADO.
   Una devolución sobre una venta contraentrega sin cobrar se rechaza (409 `DOMICILIO_SIN_COBRAR`).
6. **Reportes:** el reporte por turno cuenta `COBRO_DOMICILIO` como venta de su medio; el cuadre diario cuenta en la caja
   `VENTA`, `AJUSTE_PAGO` y `COBRO_DOMICILIO`, deja fuera las líneas de servicio y descuenta lo pendiente de la 030.
   `VentaResumen` lleva `tipoServicio`.

## Frontend
- **POS:** en el cobro, "Domicilio" con dirección (prellenada del cliente), teléfono y referencia; agrega la línea del
  envío si existe; el medio "Contraentrega" solo con domicilio.
- **Ventas › Domicilios:** tablero del día por estado con tiempos, asignar, cambiar estado, cobrar contraentrega y
  configurar el precio del envío. El domiciliario ve solo los suyos, con enlace `tel:` y "Abrir en el mapa", pensado para
  celular.

## Pruebas
- Prueba de punta a punta contra MySQL: venta contraentrega con envío (el servicio no mueve stock), estados con
  historial y transiciones inválidas, domiciliario que solo ve lo suyo, cobro en el turno del cajero y caja que cuadra,
  domicilio devuelto que devuelve el stock, fuga entre empresas.
- `VentasPorTurnoTest`: el cobro de domicilio cuenta como venta de su medio.
- Frontend: domicilio en el POS, tablero y vista del domiciliario.

Verificación final: `./mvnw verify`, `npm test`, tipos, e2e y revisión en Docker con vendedor, cajero y domiciliario a
360 px.
