# Tasks 029

- [x] T1: Confirmar que la 022 está mezclada y tomar D16 (2026-11-26, tal como está; supervisor con permiso y cajero con PIN)
- [x] T2: Reservar la migración y crear `V51__cambio_pago_venta.sql` (tipo `AJUSTE_PAGO`, acción `CAMBIAR_PAGO`,
  `venta_pago_cambio`, permisos)
- [x] T3: Anular una venta de un turno cerrado con permiso (dinero en el turno de quien anula, stock, cartera); las
  anulaciones revierten el neto de `VENTA` y `AJUSTE_PAGO`; pruebas
- [x] T4: Cambiar el medio de pago (reglas, pagos vigentes, ajuste de caja, historial, auditoría); pruebas
- [x] T5: Reporte de ventas por turno con el ajuste de pago (`VentasPorTurnoTest`); tiquete con "Pago corregido"
- [x] T6: Pruebas de fuga (`AislamientoEmpresasTest.elPagoSeCorrigeYLaFacturaDeOtroTurnoSeAnulaConPermiso...`)
- [x] T7: Facturas del turno y detalle de Ventas: diálogo de cambio de pago (`cambio-pago.spec.ts`) y anular de otro
  turno (`ventas.spec.ts`); historial "Pago corregido"
- [ ] T8: `./mvnw verify` (920 en verde, 1 omitida, antes de sumar la prueba del reporte), `npm test` (327 en verde),
  tipos regenerados y `tsc` sin errores. **Falta:** e2e del CI (corre en el PR) y revisión en Docker con el cajero y el
  supervisor (cambiar pago en el turno, anular una factura de ayer).
- [x] T9: Verificar los criterios de aceptación y actualizar el índice de specs
