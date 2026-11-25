# Tasks 021

- [x] T1: Tomar la decisión D20 y anotarla en `agents/specs/README.md` y en la spec
- [x] T2: Reservar la migración y crear `V42__categoria_movimiento_caja.sql` (tabla, columna, siembra por empresa)
- [x] T3: Entidad, repositorio, servicio y controlador de categorías; siembra para empresas nuevas; pruebas
- [x] T4: `categoriaId` en retiros, gastos e ingresos con sus reglas; respuesta con la categoría; pruebas
- [x] T5: El cajero lista las ventas de su turno abierto (y solo de ese); `medios` en el resumen; pruebas
- [x] T6: `GET /api/turnos/{id}/esperado` con los permisos de la decisión D20; pruebas
- [x] T7: Pruebas de fuga en `AislamientoEmpresasTest`
- [x] T8: Mi turno: facturas del turno con paginación, reimprimir y detalle; pruebas
- [x] T9: Mi turno: ingreso y gasto con categoría; cierre según D20; pruebas
- [x] T10: Configuración › Caja › Categorías de ingresos y egresos; pruebas
- [ ] T11: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker (hechos: `./mvnw verify` con 860 pruebas,
  `npm test` con 291 y `tsc --noEmit`; faltan `npm run build`, que corre en el CI, y la revisión en Docker con el cajero
  y el supervisor)
- [x] T12: Verificar los criterios de aceptación y actualizar el índice de specs
