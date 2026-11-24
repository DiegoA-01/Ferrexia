# 013 - Menú lateral desplegable y categorías compactas en Inicio

**Estado:** Hecha (2026-11-23, con el ajuste de Inicio; sin PR todavía) · **Rama:** `feature/menu-desplegable` (sale de `feature/inicio-categorias`) ·
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
- Se puede tener más de una categoría abierta a la vez. **Reemplazado por la spec 017 (2026-11-24): ahora solo una categoría abierta a la vez.**
- Lo que la persona abre o recoge se recuerda en ese navegador al volver a entrar.
- Los contadores (solicitudes por aprobar, productos bajo el mínimo) también se ven en la categoría recogida, para no
  perder el aviso.
- Funciona igual con teclado (Enter o Espacio abre y cierra) y en celular.

**Inicio**
- Cada tarjeta muestra solo el icono, el nombre de la categoría y cuántas pantallas tiene ("5 pantallas"); ya no lista
  las pantallas.
- **Ajuste del 2026-11-23 (pedido del usuario):** la tarjeta no despliega nada. Un clic entra directo a la categoría:
  abre la primera pantalla de esa categoría que la persona puede usar (la primera de su lista en la barra lateral, sin
  candado). Por ejemplo, el dueño entra a Ventas por "Pedidos de venta" y el cajero por "Punto de venta". Al llegar, la
  barra lateral abre esa categoría, así se ven sus demás pantallas.
- Si todas las pantallas de la categoría tienen el módulo apagado, la tarjeta sale con candado y no navega.
- Las reglas de qué ve cada persona no cambian: mismas categorías y pantallas que en la barra lateral, y candado si el
  módulo no está contratado.

## Criterios de aceptación
- [x] La barra lateral muestra las categorías recogidas, con la de la pantalla actual abierta.
- [x] Un clic (o Enter) en una categoría la abre o la recoge; varias pueden estar abiertas.
- [x] Al recargar la página se conservan las categorías que la persona dejó abiertas.
- [x] Una categoría recogida muestra la suma de los contadores de sus pantallas.
- [x] Las tarjetas de Inicio muestran icono, nombre y número de pantallas; un clic entra a la primera pantalla de la
  categoría que la persona puede usar, sin desplegar nada.
- [x] Una tarjeta cuyas pantallas tienen todas el módulo apagado sale con candado y no navega.
- [x] Nadie gana ni pierde acceso a ninguna pantalla.

> Verificado el 2026-11-23 (barra lateral): `shell.spec.ts` (categoría de la ruta abierta, abrir y recoger,
> guardado y recuperado, contador en la categoría recogida). En Docker: abrir Reportes se guarda, al entrar a Clientes
> se abre Ventas sin cerrar Reportes, y en celular el panel despliega categorías sin scroll horizontal.

> Verificado el ajuste de Inicio (2026-11-23): `inicio.spec.ts` (cada tarjeta es un enlace a la primera pantalla
> disponible según el rol, salta las de módulo apagado, candado sin enlace si no queda ninguna); 219 pruebas en verde y
> build sin advertencias. En Docker con el auditor: la tarjeta Reportes abre el Tablero del dueño y la barra lateral
> abre Reportes; ninguna tarjeta despliega nada.

## Fuera de alcance
- Esconder toda la barra lateral (modo solo iconos).
- Cambiar permisos, rutas o el orden de las categorías.
