# 023 - Cierre de caja contando billetes y monedas

**Estado:** En construcción (2026-11-25) · **Rama:** `feature/cierre-por-denominacion` · **Depende de:** 021 · **Responsable:** Persona 1 ·
**Puntos de la reunión 2:** 50 · **Migración:** V45

## Objetivo
Que contar la caja al cerrar sea más fácil y con menos errores: el cajero dice cuántos billetes y monedas tiene de cada
valor y el sistema suma. Y que quede una vista de cuánto se vendió en cada turno.

## Historias
- Como cajero, al cerrar quiero escribir cuántos billetes de $100.000, $50.000… y cuántas monedas tengo, y que el
  sistema sume.
- Como supervisor, quiero ver cuánto se vendió en cada turno.

## Comportamiento
- **Conteo por denominación:** el cierre muestra una fila por billete y moneda colombiana vigente (de $100.000 a $50)
  donde se escribe la cantidad; el sistema calcula el subtotal de cada una y el total del efectivo. También se puede
  escribir una referencia por fila y una nota general.
- El total contado reemplaza al número que hoy se escribe a mano; el resto del cierre (diferencia, tolerancia,
  aprobación) sigue como en la spec 003.
- El conteo por denominación queda guardado con el turno y se ve en su detalle.
- **Ventas por turno:** una vista con cada turno (caja, cajero, apertura, cierre) y sus ventas por método de pago,
  devoluciones, ingresos, egresos y diferencia.

## Criterios de aceptación
- [ ] El cierre permite contar por billete y moneda y calcula el total en Java.
- [ ] El conteo queda guardado y se ve en el detalle del turno.
- [ ] Hay una vista de ventas por turno con sus totales.
- [ ] Prueba de fuga entre empresas para el conteo.

## Fuera de alcance
- Monedas extranjeras.
- Cambiar la tolerancia o la aprobación de diferencias.
