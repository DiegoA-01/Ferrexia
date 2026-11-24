# 013 - Menú lateral desplegable y categorías compactas en Inicio

**Estado:** Hecha (2026-11-23, sin PR todavía) · **Rama:** `feature/menu-desplegable` (sale de `feature/inicio-categorias`) ·
**Depende de:** 011, 012

## Objetivo
Que la pantalla se vea más limpia. Hoy la barra lateral muestra las 32 opciones abiertas y hay que bajar mucho para
llegar a Reportes o Gestión; y las tarjetas de Inicio repiten esa misma lista completa.

## Historias
- Como usuario, quiero ver en la barra lateral solo las categorías y abrir la que necesito.
- Como usuario, quiero que Inicio me muestre las categorías de un vistazo, sin la lista larga de pantallas.

## Comportamiento
**Barra lateral**
- Cada categoría (Ventas, Compras, Inventario, Reportes, Gestión humana, Gestión) se muestra como un renglón con su
  icono, su nombre y una flecha. Al hacer clic se despliega o se recoge la lista de sus pantallas.
- Al entrar, todas las categorías salen recogidas, menos la que contiene la pantalla abierta (si estoy en Clientes,
  Ventas sale abierta). Inicio sigue siempre visible arriba.
- Se puede tener más de una categoría abierta a la vez.
- Lo que la persona abre o recoge se recuerda en ese navegador al volver a entrar.
- Los contadores (solicitudes por aprobar, productos bajo el mínimo) también se ven en la categoría recogida, para no
  perder el aviso.
- Funciona igual con teclado (Enter o Espacio abre y cierra) y en celular.

**Inicio**
- Cada tarjeta muestra solo el icono, el nombre de la categoría y cuántas pantallas tiene ("5 pantallas"); ya no lista
  las pantallas.
- Al hacer clic en la tarjeta se despliega dentro de ella la lista de sus pantallas (con su icono y su enlace); otro
  clic la recoge. Todas salen recogidas al entrar.
- Las reglas de qué ve cada persona no cambian: mismas categorías y pantallas que en la barra lateral, y candado si el
  módulo no está contratado.

## Criterios de aceptación
- [x] La barra lateral muestra las categorías recogidas, con la de la pantalla actual abierta.
- [x] Un clic (o Enter) en una categoría la abre o la recoge; varias pueden estar abiertas.
- [x] Al recargar la página se conservan las categorías que la persona dejó abiertas.
- [x] Una categoría recogida muestra la suma de los contadores de sus pantallas.
- [x] Las tarjetas de Inicio salen recogidas, con icono, nombre y número de pantallas; un clic muestra sus enlaces.
- [x] Nadie gana ni pierde acceso a ninguna pantalla.

> Verificado el 2026-11-23: `shell.spec.ts` (categoría de la ruta abierta, abrir y recoger, guardado y recuperado,
> contador en la categoría recogida) e `inicio.spec.ts` (tarjetas recogidas con "N pantallas" que se despliegan); 219
> pruebas en verde y build sin advertencias. En Docker con el auditor: tarjetas compactas en una rejilla de tres
> columnas, la tarjeta Compras despliega sus 9 enlaces, abrir Reportes en la barra lateral se guarda, al entrar a
> Clientes se abre Ventas sin cerrar Reportes, y en celular el panel lateral despliega categorías sin scroll horizontal.

## Fuera de alcance
- Esconder toda la barra lateral (modo solo iconos).
- Cambiar permisos, rutas o el orden de las categorías.
