# Plan 028

Rama `feature/variantes-producto`, sacada de `develop` actualizado (`16fc4f5`, después de la 026). Migración **V50**
(reservada en el índice).

## Lo que hay hoy (revisado en el código)
- Todo el sistema trabaja con `producto_id`: stock por bodega (`stock_producto_bodega`), kardex (`movimiento_inventario`),
  precios (`precio_producto`), ventas, pedidos de venta, solicitudes, RFQ, cotizaciones, órdenes, recepciones, despachos,
  contratos, reorden, rotación y reportes. El stock solo cambia por `MovimientoInventarioService`.
- No hay ningún concepto de "producto padre" ni de atributos.
- Los puntos por donde un producto entra a una operación son: `MovimientoInventarioService.productoActivo` (ajustes,
  traslados, recepción, venta), `SolicitudService` (de ahí salen RFQ, cotización y orden), `ContratoService`,
  `DespachoService`, `VentaService`, `PedidoVentaService`, `PreciosService` y `PrecioCargaService`.

## Diseño (aprobado): cada variante es un producto
Cada variante es un producto normal con su propio código, código de barras, stock, kardex y precio, agrupado bajo un
**producto agrupador** que no tiene stock ni se vende ni se compra. La alternativa (tabla `variante` con stock propio)
obligaba a cambiar stock, kardex, precios, ventas y compras; se descartó.

## Backend
1. **V50** sobre `producto`. Los atributos van como JSON en la misma fila: no hacen falta tablas nuevas y el
   `@TenantId` del producto ya los aísla por empresa.
   - `agrupador_id BIGINT NULL` (FK a `producto`), `es_agrupador TINYINT(1) NOT NULL DEFAULT 0`.
   - `atributos JSON NULL` en el agrupador: `[{"nombre":"Medida","valores":["1\"","2\""]}]`.
   - `combinacion JSON NULL` en la variante: `{"Medida":"1\""}`.
   - CHECK: un agrupador no tiene `agrupador_id`.
2. **`Producto`:** los campos nuevos y `exigirNoAgrupador()` (409 `PRODUCTO_AGRUPADOR`).
3. **`VarianteService`:**
   - `crear` (`POST /api/productos/con-variantes`): datos comunes (código y nombre del agrupador, descripción,
     categoría, unidad, impuesto), de 1 a 3 atributos con valores (sin repetir, sin distinguir mayúsculas) y una fila por
     combinación con código, código de barras, costo, precio, mínimo y máximo. Java arma el producto cartesiano, exige
     una fila por combinación (400 si falta o sobra), máximo 100 (400), valida códigos y códigos de barras repetidos
     entre sí y con la base (409 `CODIGO_DUPLICADO`, `CODIGO_BARRAS_DUPLICADO`) y crea el agrupador y las variantes en
     una transacción. El agrupador queda `vendible = false`, mínimo 0 y sin costo. El precio, si viene, se fija en la
     lista General con `PreciosService.fijarPrecio` (exige poder fijar precios; si no, 403 antes de crear nada).
   - `variantes` (`GET /api/productos/{id}/variantes`): el agrupador con sus atributos, su stock total (suma) y sus
     variantes con su combinación y su stock.
   - `cambiarValores` (`PUT /api/productos/{id}/variantes`): mismos nombres de atributos (409 `ATRIBUTOS_FIJOS` si
     cambian); las combinaciones nuevas deben venir con sus datos (400 si falta); las que ya existen se reactivan; las
     que desaparecen se desactivan.
4. **`ProductoService`:**
   - Editar un agrupador propaga nombre (rearma el de cada variante), categoría, unidad e impuesto; la unidad solo
     cambia si ninguna variante tiene movimientos (409 `UNIDAD_EN_USO`). El agrupador sigue sin mínimo ni costo.
   - Editar una variante: la categoría, la unidad y el impuesto deben ser los del agrupador (409 `VARIANTE_HEREDA`); el
     nombre se rearma siempre.
   - Desactivar el agrupador desactiva sus variantes.
   - Listado: parámetro `agrupadores` (`false` = sin agrupadores, para los selectores de compras, inventario,
     despachos y contratos). La respuesta suma `agrupadorId`, `esAgrupador` y `combinacion`.
5. **Bloqueos:** los ocho puntos de entrada de arriba rechazan un agrupador con 409 `PRODUCTO_AGRUPADOR` (la carga de
   precios lo informa como problema de la fila). La búsqueda del punto de venta ya deja fuera lo no vendible.
6. **Reportes:** el reporte de inventario lleva `agrupadorId` y `esAgrupador`; la fila del agrupador trae como stock la
   suma de sus variantes (informativa, sin valor ni alerta). La rotación deja fuera los agrupadores.

## Frontend
- **Formulario de producto (Inventario):** interruptor "Tiene variantes" al crear. Al activarlo: atributos con sus
  valores separados por coma, y una tabla generada con una fila por combinación para código, código de barras, costo,
  precio, mínimo y máximo, con "Llenar todos" para copiar un valor a todas las filas. Los códigos se proponen solos
  (`CODIGO-VALOR`).
- **Editar un agrupador:** sus datos comunes y "Variantes" para agregar o quitar valores (las combinaciones nuevas piden
  sus datos). Editar una variante deja bloqueados categoría, unidad e impuesto.
- **Inventario:** el agrupador sale como fila desplegable con sus variantes y su stock.
- **Selectores (`BuscadorProducto`) de compras, movimientos, despachos y contratos:** piden `agrupadores=false`, así que
  ofrecen las variantes con su nombre completo. El punto de venta busca por código de barras o nombre y no ve el
  agrupador.

## Pruebas
- `VarianteServiceTest`: 2 atributos × 3 valores crean 6 variantes con sus códigos y nombres; código repetido 409;
  código de barras repetido 409; más de 100 combinaciones 400; falta una combinación 400; agregar un valor crea, quitar
  desactiva, volver a poner reactiva; cambiar el nombre de un atributo 409.
- `ProductoServiceTest`: editar el agrupador propaga; la unidad no cambia si una variante tiene movimientos; editar una
  variante con otra categoría 409; desactivar el agrupador desactiva las variantes.
- Movimiento, venta, solicitud y precio sobre un agrupador dan 409; sobre una variante funcionan como un producto.
- `AislamientoEmpresasTest`: las variantes y atributos de A no se ven en B (404).
- `ReporteServiceTest`: el agrupador suma el stock de sus variantes.
- Frontend: tabla de combinaciones, "Llenar todos", envío del alta, fila desplegable en inventario y selector sin
  agrupadores.

Verificación final: `./mvnw verify`, `npm test`, revisión de tipos (`npx tsc`), e2e del CI y revisión en Docker (crear
un producto con variantes, comprarlo, recibirlo y venderlo por código de barras).
