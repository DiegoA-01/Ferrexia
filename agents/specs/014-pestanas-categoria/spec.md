# 014 - Pestañas de la categoría en cada pantalla

**Estado:** Hecha (2026-11-23, sin PR todavía) · **Rama:** `feature/pestanas-categoria` (sale de `feature/menu-desplegable`) ·
**Depende de:** 011, 013

## Objetivo
Que al entrar a una categoría (por ejemplo, desde la tarjeta Reportes de Inicio) la persona vea arriba todas las
pantallas de esa categoría y pueda pasar de una a otra sin volver a la barra lateral. Hoy, al llegar al Tablero del
dueño, no hay nada en la pantalla que muestre que existen Reportes de compras, Reportes de ventas o Rotación.

## Historias
- Como dueño, al entrar a Reportes quiero ver todos los reportes disponibles arriba y cambiar de uno a otro con un clic.
- Como cajero, al entrar a Ventas quiero ver Punto de venta, Mi turno, Pedidos de venta, Ventas, Clientes y Cartera
  juntos arriba.

## Comportamiento
- En toda pantalla que pertenezca a una categoría aparece, debajo de la barra del buscador y antes del contenido, una
  fila de pestañas con todas las pantallas de esa categoría, en el orden del menú, cada una con su icono.
- La pestaña de la pantalla actual sale resaltada (naranja). Un clic en otra pestaña abre esa pantalla y la fila sigue
  ahí.
- La categoría se deduce de la pantalla abierta, también en sus subpáginas (por ejemplo, el detalle de una orden sigue
  mostrando las pestañas de Compras). Si una ruta podría ser de dos categorías, gana la opción con la ruta más larga
  (`/app/ventas/reportes` es de Reportes, `/app/ventas` es de Ventas).
- Cada persona ve solo las pestañas de su rol, igual que en la barra lateral; una pantalla de un módulo no contratado
  sale con candado y no navega.
- No aparece en Inicio ni en pantallas que no están en el menú (por ejemplo, el perfil).
- En celular la fila se desliza de lado, sin desbordar la página.
- La barra lateral no cambia.

## Criterios de aceptación
- [x] Al entrar al Tablero del dueño aparecen arriba las pantallas de Reportes que el rol puede ver (cinco para el
  dueño, que no tiene "Reportes de compras"; cinco para el auditor, que no tiene "Informe de compras"), con el Tablero resaltado.
- [x] Un clic en otra pestaña abre esa pantalla y la resalta.
- [x] En una subpágina (por ejemplo, el detalle de una orden) se ven las pestañas de su categoría.
- [x] `/app/ventas/reportes` muestra las pestañas de Reportes y `/app/ventas` las de Ventas.
- [x] Las pestañas respetan el rol y el candado por módulo, igual que la barra lateral.
- [x] En Inicio y en el perfil no aparece la fila.
- [x] En ancho de celular no hay scroll horizontal de la página.

> Verificado el 2026-11-23: `menu.spec.ts` (`seccionDeRuta`, subpáginas y rutas parecidas) y `shell.spec.ts` (fila
> por rol, activa con `aria-current`, subpágina, candado, sin fila en Inicio ni perfil); 226 pruebas en verde y build
> sin advertencias. En Docker con el auditor: la tarjeta Reportes abre el Tablero con cinco pestañas y el Tablero
> activo; la pestaña Reportes de ventas abre su pantalla y queda activa; `/app/ordenes/1` muestra las pestañas de
> Compras; a 375 px la fila se desliza (975 px de contenido en 351 px) y la página no tiene scroll horizontal.

## Fuera de alcance
- Cambiar la barra lateral, los permisos o las rutas.
- Pestañas dentro de una misma pantalla (por ejemplo, filtros o subvistas).
