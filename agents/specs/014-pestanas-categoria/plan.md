# Plan 014

Cambio solo de frontend. Sin migración, sin endpoints y sin tocar permisos.

## Dónde
- `core/navegacion/menu.ts`: función nueva `seccionDeRuta(secciones, url)` que devuelve la categoría (con título) que
  contiene la ruta, usando la opción cuya ruta coincide más largo (`url === ruta` o `url` empieza con `ruta + '/'`, sin
  `?` ni `#`). Es la regla que hoy vive dentro de `shell.ts` (`abrirLaDe`); la barra lateral y las pestañas la usan
  las dos.
- `layout/shell/shell.ts`:
  - Señal `url` con la ruta actual (se actualiza en cada `NavigationEnd`).
  - `categoriaActual = computed(() => seccionDeRuta(secciones(), url()))`; `null` en Inicio o fuera del menú.
  - `abrirLaDe` pasa a usar `seccionDeRuta`.
- `layout/shell/shell.html`: dentro de `<main class="principal">`, antes de `<router-outlet />`, un
  `<nav class="pestanas" aria-label="Pantallas de {categoría}">` solo si hay `categoriaActual`. Cada pestaña es un
  `<a routerLink routerLinkActive="activa" [routerLinkActiveOptions]="{ exact: !!op.exacta }">` con icono y texto;
  si `bloqueada(op)`, un `<span>` con candado. `aria-current="page"` en la activa (lo pone `routerLinkActive`).
- `layout/shell/shell.scss`: fila blanca con borde y esquinas como las tarjetas; pestañas tipo píldora, la activa con
  el naranja suave de la marca; `overflow-x: auto` y sin salto de línea en celular.

## Pruebas
- `menu.spec.ts`: `seccionDeRuta` con `/app/tablero` → Reportes; `/app/ventas/reportes` → Reportes; `/app/ventas` →
  Ventas; `/app/ordenes/15` → Compras; `/app/inicio` y `/app/perfil` → `null`.
- `shell.spec.ts`:
  - En `/app/tablero` con `AUDITOR`: cinco pantallas de Reportes (sin "Informe de compras") y la activa es el
    Tablero; con `ADMIN_SISTEMA`, cinco (sin "Reportes de compras").
  - En `/app/inicio` no hay fila.
  - Con un módulo apagado, la pestaña sale con candado y sin enlace.
  - Con `CAJERO` en `/app/pos`, la fila muestra las seis pantallas de Ventas del cajero.

Verificación final: `npm test`, `npm run build` sin advertencias y revisión en Docker (Reportes desde Inicio, cambio de
pestaña, una subpágina y ancho de celular).
