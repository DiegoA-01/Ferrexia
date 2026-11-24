# Tasks 017

- [x] T1: Punto de miles en `formato.ts` (protegido con prueba) y números crudos en plantillas
- [x] T2: Directiva `scCifra` (punto de miles al escribir, coma decimal, número limpio al modelo) con sus pruebas
- [x] T3: Cambiar los 63 `type="number"` por `scCifra` y ajustar las pruebas de esas pantallas
- [x] T4: Flechas ocultas y rueda del mouse bloqueada para cualquier campo de número que quede
- [x] T5: Tablas en tarjetas bajo 768 px (`tablas-responsivas`) y corrección de desbordes; pruebas
- [x] T6: Pestañas fijas y delgadas al bajar
- [x] T7: Una sola categoría abierta en el menú; ajustar pruebas de la 013 y anotar el cambio en su spec
- [x] T8: Formato de miles en Excel y PDF del backend (si falta) con pruebas
- [x] T9: E2E de scroll horizontal y pestañas a 360, 768 y 1280 px
- [x] T10: `npm test`, `npm run build`, `./mvnw verify` si aplica, y revisión en Docker pantalla por pantalla
- [x] T11: Verificar los criterios de aceptación y actualizar el índice de specs

Notas (2026-11-24):
- El plan suponía que `es-CO` no agrupaba 4 cifras: no es así (se comprobó en Node y en el navegador). Se corrigió el plan;
  queda una prueba que protege "1.234". `useGrouping: 'always'` no existe en los tipos ES2022 del proyecto.
- Se convirtieron 64 campos (63 de pantallas y uno en un comentario que se restauró). Los manejadores que hacían
  `$event === '' ? null : +$event` pasaron a `$event` (si no, un campo vacío daba 0).
- Fallo real encontrado en la exportación: el PDF de reportes de ventas imprimía las unidades como
  "Cantidad[valor=...]" y los conteos sin punto de miles. Corregido con prueba. El Excel ya tenía formato de miles.
- La revisión en Docker de las pantallas con formulario se hizo con el cajero (el auditor solo lee).
- El e2e nuevo falló en el CI: recargaba la página en cada pantalla (unas 100 veces), cada recarga renovaba la sesión y
  el límite de 20 peticiones por minuto de /api/auth/refresh mandaba al ingreso. En local las pruebas de scroll daban
  verde midiendo la pantalla de ingreso. Ahora navega dentro de la app sin recargar y falla si la sesión se pierde;
  probado en local con Chrome contra Docker (3 de 3).
