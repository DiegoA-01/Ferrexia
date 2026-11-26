# Tasks 027

- [x] T1: Búsqueda para vender con `categoriaId`; pruebas (`VentaProductoServiceTest`)
- [x] T2: `GET /api/ventas/categorias` con conteo; pruebas y prueba de fuga (`AislamientoEmpresasTest`)
- [x] T3: Fila de categorías y grilla de productos en el POS y el pedido de venta; pruebas (`pos.spec.ts`)
- [x] T4: Cobro en un paso con cliente y pago juntos (atajos F4, F9, E); pruebas (`pos.spec.ts`)
- [x] T5: Ajustar el e2e `venta-completa`
- [ ] T6: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker — hechos: `./mvnw verify` en verde (con
  MySQL real y Javadoc; la foto de acceso de la 022 se actualizó solo en la firma de `buscarParaVender`, que ganó el
  parámetro `categoriaId` con el mismo permiso), `npm test` (304) y `tsc` sin errores; en Docker e2e 5 de 5 (la venta
  agrega un producto por categoría y el cobro muestra el cliente) y revisado como vendedor: botones Todas, Construcción,
  Eléctrico, Herramientas y Pintura con su conteo, y tarjetas con precio y stock. Falta el build (CI) y la aprobación
- [ ] T7: Verificar los criterios de aceptación y actualizar el índice de specs

Notas (2026-11-25):
- Se trabaja en la misma rama que la 023 (`feature/cierre-por-denominacion`) por pedido del equipo, para subir todo junto
  sin choques. La rama `feature/venta-agil` del plan no se usa.
- El pedido de venta del vendedor se arma en la misma pantalla del POS (sin caja), así que las categorías salen en los
  dos casos con el mismo código.
- `GET /api/ventas/productos` ahora acepta `categoriaId` y `q` pasa a ser opcional; sin ninguno de los dos responde 400.
  Con texto y categoría, se busca por texto y quedan solo los de la categoría.
- Las categorías que salen son las activas con al menos un producto activo, vendible y con precio vigente en la lista
  General; el botón muestra cuántos tiene. Una tarjeta sin precio sale desactivada.
- El cobro tiene arriba el bloque de cliente (buscar, crear o consumidor final) y abajo el pago (spec 016). F4 dentro
  del cobro lleva al buscador de cliente del cobro; Esc cierra primero el formulario de cliente nuevo. Si se vuelve a
  consumidor final con crédito o saldo a favor elegido, ese medio se borra. El panel de cliente del costado sigue y lo
  elegido ahí llega cargado al cobro.
