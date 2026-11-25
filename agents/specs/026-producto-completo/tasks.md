# Tasks 026

- [ ] T1: Tomar D17 y decidir si el cambio de unidad aplica a productos con movimientos; anotarlo
- [ ] T2: Reservar la migración y crear `V47__producto_referencia_y_maximo.sql` (proveedor y fecha de referencia, evento
  `STOCK_SOBRE_MAXIMO` por empresa, NIT opcional si la 031 no está)
- [ ] T3: Alta de producto con impuesto, proveedor y fecha de referencia; pruebas
- [ ] T4: La recepción actualiza proveedor y fecha del costo; pruebas
- [ ] T5: Cambio de unidad con conversión (si se aprobó); pruebas
- [ ] T6: Aviso y filtro de stock sobre el máximo; pruebas
- [ ] T7: Alta mínima de proveedor (`POST /api/proveedores`) si la 031 no está; pruebas
- [ ] T8: Pruebas de fuga
- [ ] T9: Formulario de producto con los campos nuevos y diálogos "Nueva categoría" y "Nuevo proveedor"; pruebas
- [ ] T10: Diálogo de cambio de unidad y filtro "Sobre el máximo"; pruebas
- [ ] T11: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker
- [ ] T12: Verificar los criterios de aceptación y actualizar el índice de specs
