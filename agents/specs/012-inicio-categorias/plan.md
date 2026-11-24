# Plan 012

Cambio solo de frontend. Sin migración, sin endpoints y sin tocar permisos.

## Dónde
- `core/navegacion/menu.ts`:
  - `SeccionMenu` suma `icono?: string`; cada categoría lleva el suyo (la sección de Inicio, ninguno).
  - Función nueva `seccionesVisibles(tieneRol)`: devuelve las secciones con solo las opciones del rol y quita las que
    quedan vacías. Es la regla que hoy vive dentro de `shell.ts`; el marco y la página de Inicio la usan las dos, así
    no se separan.
- `layout/shell/shell.ts`: `secciones` llama a `seccionesVisibles` (mismo resultado que hoy).
- `features/inicio/inicio.ts` y `.html`:
  - `categorias` = `seccionesVisibles(...)` sin la sección sin título (Inicio).
  - Rejilla `<section class="accesos">` justo después del encabezado, antes de las cifras.
  - Cada tarjeta: icono grande en un círculo naranja suave, título y la lista de opciones. Una opción con
    `!sesion.tieneModulo(modulo)` se pinta como `<span>` con candado, igual que en la barra lateral.
  - El nombre `categorias` ya existe en Inicio (gasto por categoría): ese pasa a `gastoPorCategoria` para que no
    choquen.
- `features/inicio/inicio.scss`: `.accesos { display: grid; grid-template-columns: repeat(3, 1fr); }`, dos columnas
  bajo 1200 px y una bajo 720 px; tarjetas de igual alto por fila.

## Pruebas
- `menu.spec.ts`: cada categoría con título tiene icono, y son los seis acordados; `seccionesVisibles` con `CAJERO`
  no trae "Gestión" y con `ADMIN_SISTEMA` trae las siete secciones.
- `inicio.spec.ts` (nuevo o ampliado):
  - Con `ADMIN_SISTEMA` hay seis tarjetas en el orden del menú, cada una con su icono.
  - Con `CAJERO` no está "Gestión".
  - Un módulo apagado deja la opción con candado y sin `href`.
  - Los enlaces apuntan a las mismas rutas que el menú.
- `shell.spec.ts` sigue pasando sin cambios (prueba de que la regla compartida no movió nada).

Verificación final: `npm test`, `npm run build` sin advertencias, y revisión en Docker con el cajero y el auditor en
pantalla ancha y en ancho de celular.
