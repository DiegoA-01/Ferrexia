# Plan 013

Cambio solo de frontend. Sin migración, sin endpoints y sin tocar permisos.

## Barra lateral (`layout/shell/`)
- `shell.ts`:
  - Señal `abiertas` (conjunto de títulos de categoría). Al iniciar se carga de `localStorage` (clave
    `sc.menu.abiertas`, lectura y escritura dentro de `try/catch`: sin almacenamiento todo sigue funcionando).
  - En cada `NavigationEnd` se agrega la categoría que contiene la ruta actual (sin cerrar las demás).
  - `alternar(titulo)` abre o recoge y guarda.
  - `contadorDe(seccion)` suma los contadores de sus opciones, para mostrarlo con la categoría recogida.
- `shell.html`: el título de cada sección pasa a ser un `<button type="button" class="categoria">` con icono, nombre,
  contador (si está recogida) y flecha (`expand_more`), con `aria-expanded` y `aria-controls`; la lista de opciones se
  pinta solo si la categoría está abierta. La sección de Inicio (sin título) no se puede recoger.
- `shell.scss`: estilo del botón de categoría y giro de la flecha.

## Inicio (`features/inicio/`)
- `inicio.ts`: `destino(categoria)` = ruta de la primera opción sin candado (`!bloqueada`), o `null` si todas lo tienen.
  Se quitan `desplegadas` y `alternar` (ajuste del 2026-11-23: la tarjeta navega, no despliega).
- `inicio.html`: la tarjeta entera es un `<a [routerLink]="destino">` con icono, nombre, "N pantallas" y una flecha
  (`arrow_forward`); sin destino es un `<div>` con candado y `aria-disabled`. Ya no lleva lista de enlaces.
- `inicio.scss`: la rejilla queda igual (tres, dos y una columna); la tarjeta enlace se resalta al pasar el cursor.

## Pruebas
- `shell.spec.ts`:
  - Con la ruta `/app/clientes`, Ventas sale abierta y Compras recogida.
  - Un clic en Compras la abre y deja Ventas abierta; otro clic la recoge.
  - Lo abierto se guarda en `localStorage` y se recupera al crear el marco otra vez.
  - Con Compras recogida se ve el contador de solicitudes en la categoría.
- `inicio.spec.ts`:
  - Cada tarjeta es un enlace a la primera pantalla de su categoría para ese rol (dueño: Ventas → `/app/pedidos-venta`;
    cajero: Ventas → `/app/pos`) y muestra "N pantallas".
  - Si la primera pantalla tiene el módulo apagado, la tarjeta va a la siguiente que no lo tenga.
  - Con todos los módulos de una categoría apagados, la tarjeta no tiene enlace y lleva candado.
  - Se conservan orden, iconos y cajero sin Gestión.

Verificación final: `npm test`, `npm run build` sin advertencias y revisión en Docker en escritorio y celular.
