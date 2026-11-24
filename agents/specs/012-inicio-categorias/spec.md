# 012 - Categorías en la página de Inicio

**Estado:** Hecha (2026-11-23, sin PR todavía) · **Rama:** `feature/inicio-categorias` (sale de `feature/menu-por-categorias`) ·
**Depende de:** 011

## Objetivo
Que la página de Inicio (`/app/inicio`) muestre las categorías del menú como un tablero de accesos que ocupe todo el
ancho del contenido (todo lo que queda a la derecha de la barra lateral). Hoy el dueño de la ferretería entra y ve dos
cifras y una tarjeta angosta; el resto de la pantalla queda vacío.

## Historias
- Como cualquier usuario, quiero ver al entrar todo lo que puedo hacer, agrupado por categoría, y llegar a cada
  pantalla con un clic.
- Como dueño, quiero reconocer cada categoría de un vistazo por su icono.

## Comportamiento
- Debajo del saludo aparece una rejilla con una tarjeta por categoría, en el orden del menú: Ventas, Compras,
  Inventario, Reportes, Gestión humana y Gestión. Inicio no lleva tarjeta.
- Cada tarjeta lleva un icono grande, el nombre de la categoría y la lista de sus opciones como enlaces, con el icono
  de cada opción.
  | Categoría | Icono |
  |---|---|
  | Ventas | `point_of_sale` (caja registradora) |
  | Compras | `shopping_cart` (carrito) |
  | Inventario | `inventory_2` (caja de bodega) |
  | Reportes | `bar_chart` (gráfico de barras) |
  | Gestión humana | `groups` (personas) |
  | Gestión | `settings` (engranaje) |
- Cada persona ve las mismas categorías y opciones que en su barra lateral: mismas reglas de rol, y una opción de un
  módulo no contratado sale con candado y sin enlace. Una categoría sin opciones para esa persona no aparece.
- La rejilla usa todo el ancho: en pantalla grande van tres tarjetas por fila; en tableta, dos; en celular, una.
- Lo que ya tiene Inicio (cifras, gráficos, "Requiere tu atención", reorden sugerida, mejores proveedores) se conserva
  debajo de la rejilla, sin cambios.

## Criterios de aceptación
- [x] El dueño ve las seis tarjetas, cada una con su icono, en el orden del menú.
- [x] Las opciones de cada tarjeta son las mismas que muestra la barra lateral para esa persona, y cada una abre su
  pantalla.
- [x] Una opción de un módulo apagado sale con candado y no navega.
- [x] El cajero no ve la tarjeta "Gestión".
- [x] La rejilla ocupa todo el ancho del contenido y se acomoda a tres, dos o una columna según el ancho.
- [x] El resto de la página de Inicio sigue igual.

> Verificado el 2026-11-23: `inicio.spec.ts` (seis tarjetas con icono, mismos enlaces que la barra lateral, cajero sin
> "Gestión", candado con módulo apagado) y `menu.spec.ts` (214 pruebas en verde, build sin advertencias). En Docker:
> auditor con seis tarjetas, tres columnas a 1600 px, dos a 1024 px y una a 375 px sin scroll horizontal; el enlace
> "Clientes" abre su pantalla; el cajero ve cinco categorías sin "Gestión".

## Fuera de alcance
- Cambiar permisos, rutas o el menú lateral.
- Cifras nuevas en las tarjetas (por ejemplo, ventas del día dentro de "Ventas").
- Reordenar o quitar lo que ya muestra Inicio.
