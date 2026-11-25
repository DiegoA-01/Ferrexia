# Plan 027

Rama `feature/venta-agil`, sacada de `develop` actualizado. Sin migración.

## Lo que hay hoy (revisado en el código)
- **Búsqueda para vender:** `VentaProductoService.buscar(q, listaId, bodegaId)` devuelve `ProductoParaVender` (precio
  vigente, existencia, impuesto) por código, nombre o código de barras. No filtra por categoría.
- **POS** (`features/pos/pos.ts` y `pos.html`): buscador con F2, panel de cliente con F4 ("Cambiar cliente",
  "Vender como consumidor final"), carrito y cobro aparte (diálogo con F9). El vendedor arma pedidos de venta en la misma
  pantalla y el cajero los cobra desde "Pedidos de venta".
- **Cobro (spec 016):** abre sin medio elegido; voucher opcional; atajo `E` para efectivo.
- Categorías: `GET /api/categorias` (activas de la empresa).

## Backend
1. `VentaProductoService.buscar` suma `categoriaId` opcional; con `q` vacío y `categoriaId` devuelve los productos
   vendibles de esa categoría (máximo 60, ordenados por nombre). Endpoint igual con el parámetro nuevo.
2. `GET /api/ventas/categorias` (roles del POS): categorías activas que tienen al menos un producto vendible con precio
   vigente, con el número de productos (consulta agregada con `empresa_id`).
3. Nada más cambia: precio, impuestos, caja, stock e idempotencia siguen igual.

## Frontend
1. **Categorías en el POS y en el pedido de venta:** fila de botones (chips) con las categorías sobre el buscador. Elegir
   una muestra sus productos en una grilla de tarjetas (nombre, precio, existencia); un clic agrega al carrito. "Todas"
   vuelve a la búsqueda normal. En celular la fila se desliza de lado dentro de su caja (sin mover la página).
2. **Cobro en un paso:** el diálogo de cobro tiene dos bloques en la misma vista:
   - **Cliente:** consumidor final por defecto, buscador y "Crear cliente" (usa `ClienteFormulario` de la 002). Lo elegido
     en el panel del POS llega ya cargado.
   - **Pago:** medios (spec 016), montos, cambio.
   Un solo botón **Confirmar venta** (F9). Se quita el paso separado de cliente.
3. Atajos: F2, F4 y F9 se mantienen; F4 dentro del cobro lleva al bloque de cliente; `E` sigue eligiendo efectivo.

## Pruebas
- `VentaProductoServiceTest`: filtro por categoría; categoría de otra empresa no trae nada.
- Prueba del endpoint de categorías con conteo.
- `AislamientoEmpresasTest`: las categorías para vender de A no salen en B.
- `pos.spec.ts`: elegir categoría muestra sus productos y agrega al carrito; el cobro muestra cliente y pago juntos; F9
  confirma con un medio elegido; F4 dentro del cobro enfoca el cliente; el cliente del panel llega al cobro.
- `pedidos-venta.spec.ts`: el vendedor arma un pedido por categorías.
- E2E `venta-completa`: venta por categoría con cliente identificado en el mismo paso.

Verificación final: `./mvnw verify`, `npm test`, `npm run build`, e2e del CI y revisión en Docker con el vendedor y el cajero
a 360 y 1280 px.
