# 019 - Reorganizar el menú: Reportes e Importación de datos

**Estado:** Hecha (aprobada en la revisión del 2026-11-25; sin migración; falta PR) · **Rama:** `feature/reorganizar-menu` · **Depende de:** 011, 013 · **Responsable:** Persona 2 ·
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
- [x] Ventas ya no muestra reportes; Reportes los muestra todos, incluido el informe de compras.
- [x] Configuración tiene Importación de datos con la carga de productos y de precios.
- [x] Las rutas anteriores siguen abriendo la misma pantalla.
- [x] Cada rol ve las mismas pantallas que antes, solo en otra categoría.

> Verificado el 2026-11-25 con pruebas y con el e2e en Docker (5 de 5 en verde con el stack `ferrexia`; falta el CI):
> - Lo que se vio "dentro de Ventas" en la reunión: el menú ya tenía Reportes como categoría (spec 011), pero la ruta de
>   los reportes de ventas colgaba de `/app/ventas/reportes` y "Precios y margen" (que no es un reporte) estaba en
>   Reportes. Ahora los reportes de ventas están en `/app/reportes/ventas` (la ruta vieja redirige) y "Precios y margen"
>   pasó a Ventas.
> - `menu.spec.ts`: la tabla de permisos por opción sigue igual (nadie gana ni pierde acceso); Ventas trae Precios y
>   margen y Reportes trae tablero, compras, ventas, rotación e informe. `shell.spec.ts`: al abrir los reportes de ventas
>   se marca solo su pestaña. `importacion.spec.ts`: cada carga se ve solo para quien la puede usar (dueño: precios; jefe
>   de compras: productos, y precios si el dueño se los delegó; nadie en solo lectura), las cargas usan los mismos
>   endpoints de antes, la ruta vieja redirige y la de Importación va antes de la de catálogos. Frontend: 278 pruebas en
>   verde y `tsc` sin errores.

## Fuera de alcance
- Reportes nuevos (spec 034).
- Cambiar cómo funciona la carga masiva.
