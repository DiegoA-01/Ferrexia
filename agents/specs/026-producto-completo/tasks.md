# Tasks 026

- [x] T1: Tomar D17 y anotarla — tomada el 2026-11-25 tal como está: superar el máximo no bloquea, avisa
- [x] T2: V49: `producto.proveedor_referencia_id` y `producto.costo_referencia_fecha`; `proveedor.nit` opcional; canales
  del evento `STOCK_SOBRE_MAXIMO` para las empresas existentes
- [x] T3: Alta y edición con impuesto, proveedor y fecha del costo (`ProductoRequest`/`ProductoUpdateRequest`); impuesto
  inactivo o ajeno 409 `TARIFA_INVALIDA`, proveedor ajeno 409 `PROVEEDOR_INVALIDO`; pruebas (`ProductoCompletoTest`)
- [x] T4: La recepción actualiza proveedor y fecha junto con el costo de referencia
- [x] T5: Stock máximo (D17): aviso a compras al cruzarlo con una entrada (`cruzoMaximo`); `GET /api/productos/sobre-maximo`;
  marca `sobreStockMaximo`; pruebas (`MovimientoInventarioServiceTest`, `ProductoCompletoTest`)
- [x] T6: Alta corta de proveedor `POST /api/proveedores` (solo el nombre, queda habilitado); pruebas
  (`ProveedorServiceTest`)
- [x] T7: Prueba de fuga (`AislamientoEmpresasTest`): un proveedor de B no sirve en A y "sobre el máximo" de A no sale en B
- [x] T8: Frontend: impuesto, proveedor y fecha del costo en el formulario; "Nueva" categoría y "Nuevo" proveedor encima
  del formulario sin perder lo escrito; filtro y marca "Sobre el máximo"; pruebas (`producto-completo.spec.ts`)
- [ ] T9: Unidad ajustable con movimientos — **pendiente de decisión del equipo** (ver notas)
- [ ] T10: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker — hechos: `./mvnw verify` en verde (con
  MySQL real y Javadoc), `npm test` (317) y `tsc` sin errores; en Docker V49 aplicada, e2e 5 de 5 y revisado con el jefe
  de compras: "Nueva" categoría y "Nuevo" proveedor encima del formulario, impuesto (IVA 19 %, 5 %, excluido, exento),
  proveedor y fecha del costo, y el filtro "Sobre el máximo". Falta el build (CI) y la aprobación
- [ ] T11: Verificar los criterios de aceptación y actualizar el índice de specs

Notas (2026-11-25):
- Migración V49 (V46 a V48 las tomaron la 024 y la 025). Se trabaja en `feature/cierre-por-denominacion`.
- Unidad de medida: sigue la regla de hoy. Sin movimientos se cambia libremente; con movimientos el sistema lo explica
  (409 `UNIDAD_EN_USO`). Convertir el stock de todas las bodegas, el mínimo, el máximo, los costos y los precios por
  unidad de un producto con historial es delicado; el plan dejaba esa regla "por acordar". Queda para que el equipo
  decida si se hace (con un movimiento de ajuste por bodega) o se deja así.
- En la edición, impuesto, proveedor y fecha que no se envían no cambian (los clientes viejos siguen igual).
- El aviso de "sobre el máximo" llega a COMPRADOR una sola vez al cruzar el umbral, igual que el de bajo el mínimo.
