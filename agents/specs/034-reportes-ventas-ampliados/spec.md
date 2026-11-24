# 034 - Reportes de ventas ampliados

**Estado:** Propuesta · **Rama:** `feature/reportes-ventas-ampliados` · **Depende de:** 009, 019, 023, 032 ·
**Responsable:** Persona 1 · **Puntos de la reunión 2:** 47

## Objetivo
Que el dueño vea sus ventas desde todos los ángulos que pidió en la reunión, en la categoría Reportes.

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
- [ ] Están las seis vistas con filtro de fechas y sede.
- [ ] Los totales cuadran con el reporte de ventas y caja de la spec 009.
- [ ] El margen solo lo ve quien tiene permiso.
- [ ] Se exportan a Excel y PDF.
- [ ] Prueba de fuga entre empresas para los reportes.

## Fuera de alcance
- Reporte de ventas sin existencia (spec 030).
