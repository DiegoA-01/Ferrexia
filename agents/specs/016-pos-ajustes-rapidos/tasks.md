# Tasks 016

- [x] T1: Reservar V40 en `agents/specs/README.md` y crear `V40__pedido_venta_descarte.sql`
- [x] T2: Descartar con motivo (request, entidad, servicio, respuesta con quién, cuándo y motivo, auditoría) y sus pruebas
- [x] T3: Tarjeta sin últimos 4 ni voucher obligatorios en `validarTarjetas`; pruebas, incluida venta sin pagos rechazada
- [x] T4: Prueba de fuga en `AislamientoEmpresasTest` para los pedidos descartados
- [x] T5: POS: sin campo de últimos 4, voucher opcional, cobro sin medio preelegido con su mensaje (según la decisión);
  pruebas en `pos.spec.ts`
- [x] T6: Pedidos de venta: filtro "Eliminados" con quién, cuándo y motivo, y descartar con motivo; pruebas
- [x] T7: `./mvnw verify`, `npm test` y `npm run build` sin advertencias
- [x] T8: Reconstruir `api` y `web` en Docker y revisar con el cajero
- [x] T9: Verificar los criterios de aceptación y actualizar el índice de specs

Notas (2026-11-24):
- El backend ya rechazaba una venta sin pagos (`@NotEmpty`); se sumó la prueba y el bloqueo en la pantalla.
- `ultimos4` se sigue aceptando opcional en la API para clientes viejos; el POS ya no lo envía. Las ventas viejas siguen
  mostrando sus 4 dígitos en el tiquete y en el detalle.
- "Descartado" se muestra como "Eliminado" en la pantalla; el estado en la base no cambia.
- `ventas.spec.ts` tenía una prueba del descarte sin motivo: se ajustó al nuevo flujo.
