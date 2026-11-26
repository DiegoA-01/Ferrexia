# Tasks 023

- [x] T1: Reservar la migración y crear `V45__turno_conteo_denominaciones.sql`
- [x] T2: `Denominaciones` y el cálculo del conteo en Java con sus pruebas (`DenominacionesTest`)
- [x] T3: Cierre con `conteo` opcional (validaciones, contado calculado, compatibilidad sin conteo) y respuesta con el
  conteo; pruebas (`TurnoServiceTest`)
- [x] T4: Reporte de ventas por turno (`/api/reportes/caja/turnos`) y su Excel (`/api/exportaciones/reportes/caja/turnos`)
  con sus pruebas (`VentasPorTurnoTest`) y prueba de fuga (`AislamientoEmpresasTest`)
- [x] T5: Cierre en pantalla con billetes y monedas y total en vivo; pruebas (`turno-formularios.spec.ts`, e2e
  `venta-completa` ajustado)
- [x] T6: Conteo en el detalle del turno (`caja.spec.ts`) y pestaña "Ventas por turno" en Reportes de ventas con
  exportación a Excel (`reportes-ventas.spec.ts`)
- [ ] T7: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker — hechos: `./mvnw verify` (886 pruebas, 1
  omitida, con MySQL real y Javadoc), `npm test` (299) y `tsc` sin errores; en Docker V45 aplicada, e2e 5 de 5 (el
  cierre cuenta un billete de $100.000) y revisado como dueño el conteo en el detalle del turno y "Ventas por turno".
  Falta el build (lo hace el CI) y la aprobación del equipo
- [ ] T8: Verificar los criterios de aceptación y actualizar el índice de specs

Notas (2026-11-25):
- Los errores del conteo siguen la regla del proyecto para reglas de negocio: 409 con su código
  (`DENOMINACION_INVALIDA`, `DENOMINACION_REPETIDA`, `CONTEO_NO_CUADRA`, `CONTADO_OBLIGATORIO`). El plan decía 400; los
  errores de forma (cantidad negativa, más de 100.000 unidades, más de 12 filas) sí son 400 por la validación del DTO.
- El billete de $1.000 y la moneda de $1.000 son dos filas: 7 billetes y 5 monedas.
- `contadoEfectivo` pasa a ser opcional si viene el conteo; sin conteo el cierre funciona como antes.
- La pantalla no envía el total del efectivo cuando hay conteo: lo suma el backend. Si todo está en 0 (sin efectivo),
  envía el total 0 sin conteo.
- "Ventas por turno" es una pestaña de Reportes de ventas (junto a Caja), con el mismo permiso `REPORTE_CAJA`, filtro por
  fechas y por caja. Las ventas son netas de anulaciones; los ingresos incluyen los abonos de cartera y los egresos suman
  gastos y retiros. El esperado es la suma del libro de caja, igual que en el cierre.
- El reporte vive en `CajaReporteService` para no cambiar los constructores del controlador ni de la exportación.
