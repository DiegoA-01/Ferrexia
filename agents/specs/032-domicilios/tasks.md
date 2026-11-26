# Tasks 032

- [x] T1: Tomar D19 (2026-11-26: tal como está; contraentrega como venta al despachar; envío como producto servicio)
- [x] T2: Reservar y crear `V53__domicilios.sql`
- [x] T3: Producto servicio sin stock (venta, anulación, devolución, inventario, selectores, reportes)
- [x] T4: Venta a domicilio y medio contraentrega; domicilio con consecutivo
- [x] T5: `DomicilioService`: listar, asignar, estados con historial, devolver o cancelar (anula), cobrar contraentrega,
  envío y domiciliarios
- [x] T6: Reportes (por turno con `VentasPorTurnoTest`, cuadre diario, tipo de servicio); prueba de punta a punta y de fuga
  (`AislamientoEmpresasTest.elDomicilioContraentregaSaleVendido...`)
- [x] T7: POS con domicilio y contraentrega (`pos.spec.ts`); tablero de domicilios y vista del domiciliario
  (`domicilios.spec.ts`); opción en el menú
- [ ] T8: `./mvnw verify` (924 en verde, 1 omitida), `npm test` (334 en verde), tipos regenerados y `tsc` sin errores.
  **Falta:** e2e del CI (corre en el PR) y revisión en Docker con vendedor, cajero y domiciliario a 360 px.
- [x] T9: Verificar los criterios de aceptación y actualizar el índice de specs
