# 019 - Reorganizar el menú: Reportes e Importación de datos

**Estado:** Propuesta · **Rama:** `feature/reorganizar-menu` · **Depende de:** 011, 013 · **Responsable:** Persona 2 ·
**Puntos de la reunión 2:** 10, 11, 28

## Objetivo
Que cada pantalla esté donde la persona la busca: todos los reportes juntos en la categoría Reportes, y la carga masiva
dentro de Configuración, en un apartado de importación de datos.

## Historias
- Como dueño, quiero encontrar todos los reportes (ventas, caja, compras, inventario) en Reportes.
- Como dueño, quiero que el informe de compras esté en Reportes.
- Como administrador, quiero importar datos (productos, precios) desde un solo lugar en Configuración.

## Comportamiento
- Los reportes que hoy están en la categoría Ventas pasan a **Reportes**. Ventas queda con lo operativo (Punto de
  venta, pedidos, clientes, caja).
- El **informe de compras** (`/app/informes`) pasa a **Reportes**.
- En **Configuración** aparece el apartado **Importación de datos**, con la carga masiva de productos y la de precios.
  Las entradas que hoy están sueltas en otras pantallas llevan a este apartado.
- Las rutas de las pantallas no cambian (los enlaces guardados siguen funcionando); solo cambia dónde aparecen en el
  menú, en Inicio y en las pestañas.
- Nadie gana ni pierde acceso a ninguna pantalla.

## Criterios de aceptación
- [ ] Ventas ya no muestra reportes; Reportes los muestra todos, incluido el informe de compras.
- [ ] Configuración tiene Importación de datos con la carga de productos y de precios.
- [ ] Las rutas anteriores siguen abriendo la misma pantalla.
- [ ] Cada rol ve las mismas pantallas que antes, solo en otra categoría.

## Fuera de alcance
- Reportes nuevos (spec 034).
- Cambiar cómo funciona la carga masiva.
