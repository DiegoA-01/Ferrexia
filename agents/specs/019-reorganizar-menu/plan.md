# Plan 019

Rama `feature/reorganizar-menu`, sacada de `develop` actualizado. Cambio solo de frontend (sin migración).

## Lo que hay hoy (revisado en el código)
- `core/navegacion/menu.ts` ya tiene la categoría **Reportes** (spec 011) con: Tablero del dueño, Reportes de compras,
  Reportes de ventas (`/app/ventas/reportes`), Rotación de productos, **Informe de compras** (`/app/informes`) y
  **Precios y margen** (`/app/precios`). Es decir, los puntos 10 y 11 ya están en el menú.
- Lo que la reunión vio "dentro de Ventas":
  - la ruta de reportes cuelga de `/app/ventas/...`, así que en las migas y en algunos enlaces aparece como parte de Ventas;
  - **Precios y margen** no es un reporte: es una pantalla de trabajo (fijar precios, carga por Excel) y está en Reportes.
- Carga masiva: la de productos está en `features/inventario/inventario.ts` (botón y diálogo con plantilla) y la de precios
  en `features/precios/precios.ts`. `features/configuracion/configuracion.html` es el índice de Configuración y no tiene un
  apartado de importación.

## Frontend
1. **Rutas de reportes:** `/app/reportes/ventas` pasa a ser la ruta de Reportes de ventas; `/app/ventas/reportes` queda
   como redirección (los enlaces guardados siguen funcionando). Se actualizan los enlaces del tablero (`tablero.html`) y las
   migas ("Inicio / Reportes / Ventas").
2. **Precios y margen** sale de Reportes y pasa a **Ventas** (después de Clientes). El margen como reporte sigue visible en
   Reportes de ventas para quien tenga permiso. Mismos roles que hoy (`ROLES_PRECIOS`).
3. **Importación de datos:** pantalla nueva `features/configuracion/importacion.ts` en `/app/configuracion/importacion`, con
   dos tarjetas que reutilizan los diálogos existentes:
   - "Productos" (carga masiva de productos, `POST /api/productos/carga-masiva` y su plantilla);
   - "Precios" (carga de precios por Excel de la spec 001).
   Cada tarjeta se muestra solo a quien hoy puede usar esa carga. Los diálogos se mueven a `core/ui/` o a
   `features/configuracion/importacion/` para no duplicar código.
4. En Inventario y en Precios, el botón de carga masiva se reemplaza por un enlace "Importar desde Excel" que lleva al
   apartado nuevo.
5. `configuracion.html` suma la tarjeta "Importación de datos" en su índice; el menú lateral no cambia (Configuración ya
   está en Gestión).
6. Inicio (spec 012/013) y las pestañas (spec 014) toman las categorías de `MENU`, así que se ajustan solos; se revisan sus
   pruebas.

## Pruebas
- `menu.spec.ts`: Reportes contiene ventas, compras, rotación, tablero e informe; Ventas contiene Precios y margen; cada rol
  ve las mismas pantallas que antes (se compara el conjunto de rutas por rol antes y después).
- Prueba de rutas: `/app/ventas/reportes` redirige a `/app/reportes/ventas`.
- `importacion.spec.ts`: el administrador de compras ve las dos tarjetas; un rol sin permiso no ve ninguna; la carga de
  productos llama al mismo endpoint que antes.
- Se ajustan `inventario` y `precios` (el botón pasa a enlace).
- E2E: el de "sin scroll" (spec 017) incluye la pantalla nueva.

Verificación final: `npm test`, `npm run build` sin advertencias, e2e del CI y revisión en Docker con el dueño, el jefe de
compras y el auditor.
