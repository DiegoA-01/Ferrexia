# Tasks 030

- [x] T1: Tomar D13 (2026-11-26: faltante pendiente; permiso para Dueño, Administrador y Supervisor)
- [x] T2: Reservar y crear `V52__venta_sin_existencia.sql` (tabla, permiso, evento)
- [x] T3: Venta con faltante (permiso, motivo, salida parcial, faltante, aviso); pruebas
- [x] T4: Regularización al entrar mercancía, en orden y sin stock negativo; pruebas
- [x] T5: Anulación y devolución con faltante pendiente; pruebas
- [x] T6: Reporte "Ventas sin existencia"; pruebas de fuga
  (`AislamientoEmpresasTest.laVentaSinExistenciaDejaUnFaltanteQueSeRegularizaSolo...`)
- [x] T7: POS con "¿Vender igual?" y motivo (`pos.spec.ts`); pestaña "Sin existencia" en Reportes de ventas
  (`reportes-ventas.spec.ts`)
- [ ] T8: `./mvnw verify` (922 en verde, 1 omitida), `npm test` (330 en verde), tipos regenerados y `tsc` sin errores.
  **Falta:** e2e del CI (corre en el PR) y revisión en Docker (vender sin existencia, recibir la mercancía y ver el
  faltante regularizado).
- [x] T9: Verificar los criterios de aceptación y actualizar el índice de specs
