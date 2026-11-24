# Plan 017

Rama `feature/interfaz-general`, sacada de `develop` actualizado. Cambio solo de frontend (sin migración). Revisa también
cómo salen las cifras en las exportaciones del backend.

## Lo que hay hoy (revisado en el código)
- **Formato:** `core/formato/formato.ts` usa `Intl.NumberFormat('es-CO')`, que ya agrupa (`1234` sale "1.234"; se comprobó
  en Node y en el navegador; solo `es` sin país no agrupa 4 cifras). Las cifras sin punto vienen de plantillas que pintan
  el número crudo (`{{ x }}`) sin `pesos` ni `numero`.
- **Campos de número:** 63 `<input type="number">` en 23 archivos (45 con `ngModel`, 3 con formularios reactivos). Muestran
  flechas, la rueda del mouse cambia el valor y no ponen punto de miles al escribir.
- **Tablas:** 152 tablas `.tabla` en 45 archivos, dentro de `.tabla-contenedor { overflow-x: auto }`: en celular se
  desplazan de lado (se vio en el carrito del POS a 1024 px).
- **Pestañas (spec 014):** la fila está arriba del contenido y se pierde al bajar.
- **Menú (spec 013):** permite varias categorías abiertas a la vez.

## Cambios
1. **Punto de miles al mostrar:** `formato.ts` ya agrupa con `es-CO`; una prueba protege el punto en 4 cifras
   (`useGrouping: 'always'` no está en los tipos de TypeScript ES2022 del proyecto y no hace falta). Se busca en las plantillas dónde se pinta un número crudo (`{{ x }}` de montos
   o cantidades) y se cambia por `pesos` o `numero`. El tiquete (`tiquete.ts`) usa los mismos formatos.
2. **Campo de cifra:** directiva nueva `scCifra` (`core/ui/cifra.ts`, `ControlValueAccessor`) para `<input scCifra>`:
   - el campo es de texto con `inputmode="decimal"` (teclado numérico en celular), sin flechas;
   - mientras se escribe pone el punto de miles y acepta la coma decimal ("1.250.000", "2,5");
   - entrega al formulario un `number` (o `null` vacío), así el backend recibe el número limpio, igual que hoy;
   - opciones `decimales` (por defecto 2; 0 para pesos enteros, 4 para cantidades) y `min`.
   Se cambian los 63 `type="number"` por `scCifra` (con `ngModel` o `formControlName`).
3. **Red de seguridad para lo que quede:** en `styles.scss` se ocultan las flechas de cualquier `input[type=number]`, y un
   escucha global en el marco evita que la rueda cambie un campo de número enfocado.
4. **Sin scroll horizontal:**
   - En pantallas de menos de 768 px, toda `table.tabla` pasa a tarjetas apiladas: cada fila es una tarjeta y cada celda
     muestra su encabezado al lado. El encabezado se copia solo a cada celda (`data-etiqueta`) con un servicio global
     (`core/ui/tablas-responsivas.ts`) que observa el DOM, así no hay que tocar las 152 tablas a mano.
   - Se corrigen las causas de desborde que aparezcan en la revisión (anchos fijos, `white-space: nowrap`, filas de
     botones sin `flex-wrap`).
   - La fila de pestañas de la 014 y los filtros conservan su propio deslizamiento de lado dentro de su caja (la página
     no se mueve).
5. **Pestañas fijas:** `.pestanas` con `position: sticky; top: 0` dentro de `.principal`; al bajar pasa a una versión
   más delgada (clase `compacta` con un `IntersectionObserver`), con fondo para que no se mezcle con el contenido.
6. **Una categoría abierta:** en `shell.ts`, `alternar` abre una y cierra las demás; al navegar queda abierta solo la de
   la pantalla actual. `localStorage` guarda solo esa. Se anota en la spec 013 que esa regla la reemplaza la 017.
7. **Exportaciones:** se revisa que el Excel (POI) tenga formato numérico con miles (`#,##0` y `#,##0.00`) y que el PDF
   (OpenPDF) formatee con punto de miles; se corrige donde no.

## Pruebas
- `formato.spec.ts`: `pesos(1234)` = "$ 1.234", `numero(1234)` = "1.234", `numero(2.5)` = "2,5".
- `cifra.spec.ts`: escribir "1250000" muestra "1.250.000" y entrega `1250000`; "2,5" entrega `2.5`; vacío entrega `null`;
  con `decimales=0` no deja coma; un valor que llega del modelo se pinta con puntos.
- `shell.spec.ts`: abrir Compras recoge Ventas; nunca hay dos con `aria-expanded="true"`; al navegar queda solo la de la
  ruta. Se ajustan las pruebas de la 013 que esperaban varias abiertas.
- `tablas-responsivas.spec.ts`: una tabla nueva recibe `data-etiqueta` en sus celdas con el texto del encabezado.
- Las pruebas de pantallas que hoy escriben en `type="number"` se ajustan al campo nuevo.
- **E2E (Playwright, en el CI):** prueba nueva que recorre las pantallas del cajero y del supervisor a 360, 768 y 1280 px
  y comprueba `document.documentElement.scrollWidth <= innerWidth`, y que las pestañas siguen visibles al bajar.
- Backend: si se toca la exportación, pruebas de que la celda de Excel lleva formato de miles.

Verificación final: `npm test`, `npm run build` sin advertencias, `./mvnw verify` si cambia el backend, el e2e del CI, y
revisión en Docker pantalla por pantalla a 360, 768 y 1280 px con el auditor, el jefe de compras y el cajero (cubren
casi todas las pantallas sin MFA).

## Decisión tomada (2026-11-24)
**Tablas en celular:** la spec dice que se "acomodan" en vez de desbordarse. Propongo **tarjetas apiladas automáticas**
para todas las tablas en pantallas de menos de 768 px. La alternativa es dejar el desplazamiento de lado dentro de la
tabla (la página no se mueve, la tabla sí), que es más barato pero no cumple del todo la spec.
**Tomada:** tarjetas apiladas.
