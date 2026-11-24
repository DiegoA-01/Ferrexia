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
- `inicio.ts`: señal `desplegadas` (conjunto de títulos, vacía al entrar) y `alternar(titulo)`.
- `inicio.html`: la cabecera de la tarjeta pasa a ser un `<button>` (icono, nombre, "N pantallas", flecha) con
  `aria-expanded`; la lista de enlaces se pinta solo si la tarjeta está desplegada.
- `inicio.scss`: la rejilla queda igual (tres, dos y una columna); `align-items: start` para que una tarjeta abierta no
  estire a las demás.

## Pruebas
- `shell.spec.ts`:
  - Con la ruta `/app/clientes`, Ventas sale abierta y Compras recogida.
  - Un clic en Compras la abre y deja Ventas abierta; otro clic la recoge.
  - Lo abierto se guarda en `localStorage` y se recupera al crear el marco otra vez.
  - Con Compras recogida se ve el contador de solicitudes en la categoría.
- `inicio.spec.ts`:
  - Las tarjetas salen sin enlaces y muestran "N pantallas".
  - Un clic en Ventas muestra sus enlaces; otro clic los esconde.
  - Se conservan las pruebas de la 012 (orden, iconos, cajero sin Gestión, candado), haciendo clic antes de mirar
    los enlaces.

Verificación final: `npm test`, `npm run build` sin advertencias y revisión en Docker en escritorio y celular.
