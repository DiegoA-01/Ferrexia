# Plan 028

Rama `feature/variantes-producto`, sacada de `develop` actualizado **después de la 026**. Migración propuesta **V48**.

## Lo que hay hoy (revisado en el código)
- Todo el sistema trabaja con `producto_id`: stock por bodega (`stock_producto_bodega`), kardex (`movimiento_inventario`),
  precios (`precio_producto`), ventas, pedidos de venta, solicitudes, RFQ, cotizaciones, órdenes, recepciones, despachos,
  reorden, rotación y reportes. El stock solo cambia por `MovimientoInventarioService`.
- No hay ningún concepto de "producto padre" ni de atributos.

## Diseño (recomendado): cada variante es un producto
Para no tocar los ~20 módulos que usan `producto_id`, **cada variante es un producto normal** con su propio código,
código de barras, stock, kardex y precio, y se agrupa bajo un **producto agrupador**:
- El agrupador no tiene stock ni se vende ni se compra; solo agrupa y define los atributos.
- Todo lo que hoy funciona con productos funciona igual con las variantes, sin cambios.

La alternativa (una tabla `variante` con su propio stock) obliga a cambiar stock, kardex, precios, ventas y compras; se
descarta por costo y riesgo.

## Backend
1. **V48:**
   - `producto.agrupador_id BIGINT NULL` (FK a `producto`) y `producto.es_agrupador TINYINT NOT NULL DEFAULT 0`.
   - `producto_atributo (id, empresa_id, producto_id, nombre, orden)` y `producto_atributo_valor (id, empresa_id,
     atributo_id, valor, orden)`, únicos por producto y nombre, y por atributo y valor.
   - `producto.combinacion JSON NULL` en la variante (por ejemplo `{"Medida": "1 1/2\""}`).
   - CHECK: un agrupador no tiene `agrupador_id`; una variante no es agrupador.
2. **Crear con variantes:** `POST /api/productos/con-variantes` con los datos comunes (nombre, categoría, unidad, impuesto),
   los atributos con sus valores y, por cada combinación, código, código de barras, costo, mínimo y máximo. Java arma las
   combinaciones (producto cartesiano), valida que no haya códigos repetidos y crea el agrupador y las variantes en una
   transacción. Máximo 100 combinaciones.
3. **Editar:** agregar un valor nuevo crea las variantes que faltan; quitar un valor **desactiva** sus variantes (nada se
   borra). Las variantes se editan como cualquier producto.
4. **Reglas:** `MovimientoInventarioService`, ventas, compras y precios rechazan un agrupador (409
   `PRODUCTO_AGRUPADOR`). La búsqueda para vender y los selectores de producto devuelven variantes con el nombre completo
   ("Tornillo drywall · 1 1/2\"").
5. **Consultas:** `GET /api/productos/{id}/variantes` y, en el listado, el stock total del agrupador como suma de sus
   variantes (solo informativo).

## Frontend
- **Formulario de producto:** interruptor "Tiene variantes". Al activarlo: atributos con sus valores (chips), y una tabla
  generada con una fila por combinación para código, código de barras, costo, mínimo y máximo (con "Llenar todos" para
  copiar un valor a todas las filas).
- **Inventario:** el agrupador sale como fila desplegable con sus variantes y su stock.
- **POS y compras:** al elegir un agrupador se abre la selección de variante; el lector de código de barras va directo a
  la variante.

## Pruebas
- `ProductoServiceTest` (o `VarianteServiceTest`): 2 atributos × 3 valores crean 6 variantes con sus códigos; código
  repetido 409; más de 100 combinaciones 400; quitar un valor desactiva sus variantes.
- Movimiento, venta y orden sobre un agrupador dan 409; sobre una variante funcionan como un producto.
- `AislamientoEmpresasTest`: atributos y variantes de A no se ven en B.
- Frontend: tabla de combinaciones, "Llenar todos", selección de variante en el POS.

Verificación final: `./mvnw verify`, `npm test`, `npm run build`, e2e del CI y revisión en Docker (crear un producto con
variantes, comprarlo, recibirlo y venderlo por código de barras).
