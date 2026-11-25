# Plan 026

Rama `feature/producto-completo`, sacada de `develop` actualizado. Migración propuesta **V47**. Decisión **D17** debe
estar tomada. Si la 031 (proveedor sin cuenta) ya está mezclada, se reutiliza su alta de proveedor; si no, esta spec crea
el alta mínima y la 031 la amplía.

## Lo que hay hoy (revisado en el código)
- **Producto:** `Producto` ya tiene `tarifaImpuesto` (V29, puede ser `null`), `vendible`, `precioIncluyeImpuesto`,
  `codigoBarras`, `stockMinimo`, `stockMaximo`, `costoReferencia`, `costoPromedio`, `plazoEntregaDias` y `diasSeguridad`.
  Pero `ProductoRequest` (alta) solo pide código, nombre, descripción, categoría, unidad, mínimo, máximo y costo de
  referencia: el impuesto se pone después desde Precios.
- **Unidad:** `ProductoService` rechaza cambiar la unidad si hay movimientos (409 `UNIDAD_EN_USO`). Las unidades son
  globales e inmutables y convierten solo dentro de la misma magnitud (`UnidadMedidaService.convertir`).
- **Stock máximo:** se usa en la reorden y el reabastecimiento; nada avisa cuando se supera.
- **Categoría:** `POST /api/categorias` existe (ADMIN_COMPRAS y ADMIN_SISTEMA).
- **Proveedor:** **no existe** un alta interna. Los proveedores solo entran por el registro público
  (`/api/portal/registro/{empresa}`) y quedan PENDIENTE. `proveedor.nit` es obligatorio (NOT NULL).
- **Costo de referencia:** la recepción lo actualiza con el último precio de la orden, pero no guarda de qué proveedor ni
  cuándo.

## Backend
1. **Alta completa:** `ProductoRequest` suma `tarifaImpuestoId` (opcional; si no viene, queda `null` como hoy),
   `proveedorReferenciaId` (opcional) y `costoReferenciaFecha` (opcional, por defecto hoy si hay costo). La validación del
   impuesto usa la tarifa de la empresa (`@TarifaImpuestoActiva`).
2. **V47:** `producto.proveedor_referencia_id BIGINT NULL` (FK a `proveedor`) y `producto.costo_referencia_fecha DATE
   NULL`. La recepción (`RecepcionService`) actualiza también estos dos con el proveedor de la orden y la fecha de la
   recepción.
3. **Unidad ajustable:**
   - Sin movimientos: se cambia libremente (ya funciona).
   - Con movimientos: nueva acción `PUT /api/productos/{id}/unidad` con `{ unidadId, motivo }` que solo acepta una unidad
     de la **misma magnitud**; convierte en Java el stock de cada bodega, el mínimo, el máximo y los costos con el factor,
     en una transacción con bloqueo de las filas de stock, y deja un movimiento `AJUSTE_UNIDAD` por bodega con el antes y
     el después (el stock no cambia en cantidad real, solo de unidad). Precios por unidad (spec 001) se recalculan o se
     marcan para revisar. Se audita.
   - Si el equipo prefiere no tocar productos con historia, se deja solo el primer caso y se documenta (anotar en D17 o en
     una decisión nueva).
4. **Stock máximo (D17):** se puede superar. `MovimientoInventarioService`, después de una entrada, si el total pasa el
   máximo y antes no lo pasaba, avisa con el evento nuevo `STOCK_SOBRE_MAXIMO` (sembrado por empresa en la V47) al rol de
   compras. `GET /api/productos?sobreMaximo=true` lista los que están por encima.
5. **Proveedor al vuelo:** `POST /api/proveedores` (ADMIN_COMPRAS) con `razonSocial` obligatorio y NIT opcional; si la
   031 no está, se hace aquí con `proveedor.nit` pasando a NULL en la V47 (el índice único por empresa ignora los NULL).
   El proveedor creado así queda HABILITADO y sin usuario.

## Frontend
- **Formulario de producto:** campos nuevos Impuesto (select con las tarifas), Proveedor de referencia (buscador) y Fecha
  del costo. Junto a Categoría y Proveedor, botón **Nueva** que abre un diálogo corto; al guardar queda elegido y el
  formulario conserva lo escrito.
- **Unidad:** si el producto tiene movimientos, el campo se reemplaza por "Cambiar unidad…" que abre el diálogo con las
  unidades de la misma magnitud, la conversión que se aplicará y el motivo.
- **Inventario:** filtro "Sobre el máximo" y marca en la fila.

## Pruebas
- `ProductoServiceTest`: alta con impuesto y proveedor; tarifa de otra empresa 409; cambio de unidad de la misma magnitud
  convierte stock, mínimo, máximo y costo; magnitud distinta 409.
- `MovimientoInventarioServiceTest`: entrada que cruza el máximo avisa una vez; la siguiente no.
- `RecepcionServiceTest`: actualiza costo, proveedor y fecha.
- Alta de proveedor mínima (solo nombre).
- `AislamientoEmpresasTest`: proveedor de referencia de otra empresa no se puede asignar; productos sobre el máximo solo
  de la propia empresa.
- Frontend: diálogo de categoría y de proveedor sin perder lo escrito; campos nuevos; cambio de unidad.

Verificación final: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker con el jefe de compras (crear un
producto completo con categoría y proveedor nuevos, recibir y ver el aviso de máximo).
