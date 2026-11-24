# Plan 016

Rama `feature/pos-ajustes-rapidos`, sacada de `develop` actualizado. Migración **V40** (se reserva en
`agents/specs/README.md` en el primer commit).

## Lo que hay hoy (revisado en el código)
- **Tarjeta:** `PagoVentaRequest` trae `ultimos4` (4 dígitos) y `referencia` (autorización). `VentaService.validarTarjetas`
  exige los dos para tarjeta débito o crédito (409 `TARJETA_SIN_DATOS`). La tabla `venta_pago` (V34) tiene `ultimos4`
  opcional con un CHECK de 4 dígitos. El POS pinta los dos campos y el tiquete imprime `****1234` si hay dato.
- **Método de pago:** el backend ya rechaza una venta sin pagos (`@NotEmpty` en `VentaRequest.pagos` y `@NotNull` en cada
  `medio`). En el POS el cobro abre con **Efectivo ya elegido**, así que nunca se ve "sin método"; en el pago dividido se
  pueden quitar todos los medios.
- **Pedidos descartados:** `pedido_venta.estado` ya tiene `DESCARTADO` y la lista ya filtra por estado, pero
  `POST /api/pedidos-venta/{id}/descartar` no pide motivo y la tabla no guarda quién ni cuándo (solo la auditoría).

## Backend
- **V40 `pedido_venta`:** `descartado_por_usuario_id BIGINT NULL` (FK a `usuario`), `descartado_en DATETIME NULL`,
  `motivo_descarte VARCHAR(300) NULL` y un CHECK: si el estado es `DESCARTADO` y hay `descartado_en`, el motivo no está
  vacío. Los descartados de antes quedan con esos campos en `NULL`.
- **Descartar con motivo:** `POST /api/pedidos-venta/{id}/descartar` recibe `{ "motivo": "..." }` (obligatorio, 3 a 300
  caracteres); guarda quién (de la sesión), cuándo y el motivo, y la auditoría lleva el motivo.
- **Respuesta del pedido:** `PedidoVentaResponse` suma `descartadoPor` (nombre), `descartadoEn` y `motivoDescarte`.
- **Tarjeta:** `validarTarjetas` deja de exigir `ultimos4` y `referencia`. `ultimos4` sigue aceptándose opcional (4 dígitos)
  para no romper clientes viejos, y sigue prohibido en medios que no son tarjeta. Las ventas viejas no cambian.
- **Método de pago:** no cambia; se suma la prueba de que la API rechaza una venta sin pagos y un pago sin medio.
- **Pruebas:** `VentaServiceTest` (tarjeta sin últimos 4 ni voucher se guarda; venta sin pagos 400), prueba del servicio de
  pedidos (descartar sin motivo 400, con motivo guarda quién, cuándo y motivo), y en `AislamientoEmpresasTest` que la
  empresa B no ve los descartados de A ni los puede descartar (404).

## Frontend
- **POS, cobro con tarjeta:** se quita el campo "Últimos 4 dígitos" (cobro simple y dividido) y `ultimos4` deja de
  enviarse. El campo de autorización pasa a "Código del voucher (opcional)". Se cambia la nota "Nunca escriba el número
  completo…" por una que no pida los 4 dígitos.
- **POS, método obligatorio:** el cobro abre **sin medio elegido**; mientras no haya uno, "Confirmar venta" queda
  desactivado y sale "Elige cómo paga el cliente". En el pago dividido, si se quitan todos los medios, igual.
  F9 no confirma sin medio.
- **Pedidos de venta:** el filtro de estado muestra "Eliminados" para `DESCARTADO`. En esa vista se ven las columnas
  "Eliminado por", "Cuándo" y "Motivo" (los viejos dicen "Sin dato, anterior al cambio"), sin botón de cobrar. "Descartar"
  pide el motivo en un diálogo antes de enviar.
- Tipos en `esquema.ts` (a mano si el backend local no arranca para `npm run api:tipos`).
- **Pruebas:** `pos.spec.ts` (tarjeta sin últimos 4 y sin voucher envía la venta; sin medio el botón está desactivado y
  sale el mensaje; las pruebas que hoy suponen Efectivo preelegido eligen el medio primero) y `pedidos-venta` (filtro
  Eliminados con quién, cuándo y motivo; descartar exige motivo).

## Decisión tomada (2026-11-24)
**¿El cobro abre sin medio elegido?** La spec pide que "no se pueda confirmar sin elegir": la forma literal es no
preelegir Efectivo. Costo: un clic más en la venta más común (efectivo). Alternativa: dejar Efectivo preelegido y solo
bloquear cuando no hay ningún medio (pago dividido vacío), que hoy ya casi no pasa. **Tomada:** no preelegir, como dice la
spec, con un atajo de teclado `E` para Efectivo dentro del cobro.

Verificación final: `./mvnw verify` (incluida la prueba de fuga con MySQL real), `npm test`, `npm run build` sin
advertencias y revisión en Docker con el cajero (cobro con tarjeta sin datos, sin medio, descartar con motivo y ver
Eliminados).
