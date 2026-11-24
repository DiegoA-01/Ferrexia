# Plan 011

Cambio solo de frontend. Sin migración, sin endpoints y sin tocar permisos.

## Dónde
- `frontend/src/app/core/navegacion/menu.ts`: la constante `MENU` se reordena en siete secciones (ver `spec.md`). Cada
  opción se mueve **tal cual**: mismos `roles`, `modulo`, `ruta`, `icono`, `exacta` y `contador`. Solo cambia el texto
  de "Reportes" a "Reportes de compras".
- **Inicio sin título:** va en una primera sección con `titulo: ''`. En `layout/shell/shell.html` el `<p class="seccion">`
  se pinta solo si la sección tiene título, para que no quede un renglón vacío arriba.
- El filtro que ya existe en `shell.ts` (`secciones`) oculta un grupo cuando la persona no ve ninguna de sus opciones;
  no hace falta cambiarlo.

## Pruebas
Archivo nuevo `core/navegacion/menu.spec.ts` (Vitest):
- Los títulos de las secciones son, en orden: `''`, Ventas, Compras, Inventario, Reportes, Gestión humana, Gestión.
- Hay 32 opciones y ninguna ruta se repite.
- Cada opción conserva sus roles y su módulo: se compara contra una tabla fija `ruta → {roles, modulo}` copiada del
  menú anterior. Si alguien cambia un permiso por error, la prueba falla.
- "Precios y margen" está en Reportes, "Proveedores" y "Catálogos de proveedores" en Compras, "Delegaciones" en Gestión
  humana, y `/app/reportes` se llama "Reportes de compras".

En `layout/shell/shell.spec.ts`:
- Con rol `CAJERO` no aparece "Gestión" y en "Gestión humana" solo está "Delegaciones"; con `ADMIN_SISTEMA` aparecen
  los seis grupos con título.
- La sección de Inicio no pinta título.

Verificación final: `npm test`, `npm run build` sin advertencias y una revisión en el navegador con tres roles (dueño,
jefe de compras y cajero) sobre la app en Docker.
