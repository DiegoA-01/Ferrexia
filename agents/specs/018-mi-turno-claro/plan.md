# Plan 018

Rama `feature/mi-turno-claro`, sacada de `develop` actualizado. Cambio solo de frontend (sin migración) más un documento
de revisión del flujo de cajas.

## Lo que hay hoy (revisado en el código)
- `features/caja/mi-turno.ts` tiene la plantilla en línea (sin `.html`). Con turno abierto pinta, una debajo de otra, todas
  las tarjetas: resumen del turno (con el aviso `debeRetirar`), "Retiro, gasto o ingreso de efectivo", "Abono de cliente"
  (si el módulo Cartera está activo) y "Cierre ciego".
- Después de cerrar, sale la tarjeta "Turno cerrado" con lo contado y la diferencia y, en la misma pantalla, el formulario
  "Abrir turno" con las cajas libres y ocupadas (spec 015). En la reunión se vio como "aparece crear turno".
- No hay un botón "Cerrar turno" arriba: el cierre es la última tarjeta y hay que bajar hasta ella.
- Backend: `GET /api/turnos/actual`, `POST /api/turnos`, `POST /api/turnos/{id}/movimientos`, `POST /api/turnos/{id}/cerrar`
  ya cubren todo lo que pide esta spec; no se tocan.

## Frontend
1. **Estados de la pantalla** (un `computed` `vista`):
   - `SIN_TURNO`: solo "Abrir turno".
   - `ABIERTO`: encabezado del turno (caja, cajero, desde qué hora) con dos botones visibles: **Ir al punto de venta** y
     **Cerrar turno**. Debajo, el selector de acción.
   - `RECIEN_CERRADO`: la tarjeta "Turno cerrado" con un botón **Abrir otro turno** que recién ahí muestra el formulario.
2. **Selector de acción:** un `mat-select` (o botones segmentados en escritorio) con "Registrar retiro", "Registrar gasto",
   "Registrar ingreso", "Abono de cliente" (solo con Cartera) y "Cerrar turno". Solo se pinta el formulario elegido. La
   opción elegida se recuerda en la URL (`?accion=gasto`) para que recargar no la pierda.
3. El botón **Cerrar turno** del encabezado elige la acción "Cerrar turno" y lleva el foco al primer campo del conteo.
4. Los formularios actuales se pasan a componentes hijos pequeños dentro de `features/caja/turno/`
   (`movimiento-caja.ts`, `abono-turno.ts`, `cierre-turno.ts`) para que la 021 y la 023 los amplíen sin tocar todo el
   archivo.
5. Atajos: ninguno nuevo. Accesibilidad: el selector con etiqueta visible y el formulario elegido recibe el foco.

## Revisión del flujo (punto 52)
Archivo `agents/specs/018-mi-turno-claro/revision-flujo-cajas.md` con:
- Recorrido actual paso a paso (abrir → vender → retiro/gasto/ingreso con PIN → abono → cerrar a ciegas → aprobar
  diferencia), quién lo hace (rol) y qué endpoint usa.
- Los problemas que se vieron en la reunión (puntos 44 a 55) y qué spec los resuelve (021, 023, 025, 029).
- Preguntas abiertas para el equipo (D15 y D20).

## Pruebas
- `mi-turno.spec.ts`:
  - sin turno: solo se ve "Abrir turno";
  - con turno abierto: no existe el formulario de abrir; se ven "Cerrar turno" e "Ir al punto de venta";
  - elegir "Registrar gasto" muestra solo ese formulario; cambiar a "Cerrar turno" lo reemplaza;
  - recién cerrado: "Abrir otro turno" muestra el formulario;
  - sin Cartera no aparece "Abono de cliente".
- Las pruebas actuales de movimientos, abono y cierre se mueven a los componentes hijos sin cambiar lo que comprueban.
- E2E `venta-completa.spec.ts`: ajustar los pasos de abrir y cerrar turno al selector.

Verificación final: `npm test`, `npm run build` sin advertencias, e2e del CI y revisión en Docker con el cajero (abrir,
gasto, retiro con PIN, cerrar y volver a abrir) a 360 y 1280 px.
