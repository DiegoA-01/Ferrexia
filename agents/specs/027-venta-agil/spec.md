# 027 - Venta ágil: categorías en el pedido y cobro en un paso

**Estado:** En construcción (2026-11-25) · **Rama:** `feature/cierre-por-denominacion` (junto con la 023) · **Depende de:** 005, 016 · **Responsable:** Persona 1 ·
**Puntos de la reunión 2:** 17, 23

## Objetivo
Que el vendedor encuentre los productos más rápido y que el cobro tenga menos pantallas.

## Historias
- Como vendedor, quiero buscar productos por categoría al armar un pedido de venta.
- Como cajero, quiero elegir el cliente y el método de pago en el mismo paso.

## Comportamiento
- **Categorías en el pedido:** el pedido de venta y el Punto de venta muestran las categorías de productos como botones
  o pestañas. Al elegir una se ven sus productos con precio y existencia; la búsqueda por código, nombre y lector sigue
  igual.
- **Cliente y pago juntos:** el cobro tiene un solo paso con dos partes: cliente (consumidor final por defecto, buscar o
  crear) y métodos de pago (spec 016: obligatorio, voucher opcional). Un solo botón confirma.
- Los atajos de teclado del POS se mantienen.

## Criterios de aceptación
- [ ] El pedido de venta y el POS permiten elegir productos por categoría.
- [ ] El cobro pide cliente y método de pago en un mismo paso.
- [ ] Los atajos de teclado siguen funcionando.
- [ ] La venta se guarda igual que antes (mismas reglas de precio, impuesto, caja e idempotencia).

## Fuera de alcance
- Imágenes de producto.
- Domicilios (spec 032).
