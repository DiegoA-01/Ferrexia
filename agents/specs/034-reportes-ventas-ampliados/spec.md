# 034 - Reportes de ventas ampliados

**Estado:** Hecha (2026-11-27; falta revisión en Docker) · **Rama:** `feature/reportes-ventas-ampliados` · **Depende de:** 009, 019, 023, 032 ·
**Responsable:** Persona 1 · **Puntos de la reunión 2:** 47 · **Migración:** V56

## Objetivo
Que el dueño vea sus ventas desde todos los ángulos que pidió en la reunión, en la categoría Reportes.

## Decisiones (2026-11-27)
- El costo y el margen los ve quien tenga el permiso nuevo `REPORTE_MARGEN`: Dueño, Administrador y Contador/Auditor.
- Se reutilizan las cinco vistas y la exportación que ya existían; se agregan tipo de servicio y filtro por sede.

## Historias
- Como dueño, quiero ver cuánto vendo por categoría, por empleado, por producto, por medio de pago, por tipo de
  servicio y por caja.

## Comportamiento
- En **Reportes › Ventas** hay estas vistas, todas con filtro de fechas (hoy, semana, mes, rango) y sede:
  - **Por categoría** de producto.
  - **Por empleado** (quien vendió).
  - **Por producto o insumo.**
  - **Por medio de pago** (efectivo, tarjeta, transferencia, crédito…).
  - **Por tipo de servicio** (mostrador o domicilio, spec 032).
  - **Por caja.**
- Cada vista muestra cantidad de ventas, unidades, total, devoluciones y, para quien tenga permiso, el margen
  (spec 008).
- Se puede exportar a Excel y PDF como los reportes actuales.
- Java calcula y redondea todos los totales; las ventas anuladas no cuentan y las devoluciones restan.

## Criterios de aceptación
- [x] Están las seis vistas con filtro de fechas y sede (se sumó "Tipo de servicio"; la sede sale si la empresa
  tiene más de una).
- [x] Los totales cuadran con el reporte de ventas y caja de la spec 009 (toda agrupación suma lo mismo que el total,
  con anuladas y devoluciones: `AislamientoEmpresasTest` y `AgregadorVentasTest`).
- [x] El margen solo lo ve quien tiene permiso (`REPORTE_MARGEN`: Dueño, Administrador y Contador/Auditor).
- [x] Se exportan a Excel y PDF (con los mismos filtros; sin permiso, sin las columnas de costo y margen).
- [x] Prueba de fuga entre empresas para los reportes (la sede de otra empresa da 404, también al exportar).

## Fuera de alcance
- Reporte de ventas sin existencia (spec 030).
