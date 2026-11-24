# Tasks 014

- [x] T1: `seccionDeRuta` en `menu.ts` con sus pruebas; `abrirLaDe` del marco la usa
- [x] T2: Fila de pestañas en `shell.html` según la categoría actual, con candado por módulo; pruebas en `shell.spec.ts`
- [x] T3: Estilos de la fila (píldoras, activa en naranja, deslizable en celular)
- [x] T4: `npm test` y `npm run build` sin advertencias
- [x] T5: Reconstruir `web` en Docker y revisar: Reportes desde Inicio, cambio de pestaña, una subpágina y celular
- [x] T6: Verificar los criterios de aceptación y actualizar el índice de specs

Notas (2026-11-23):
- Al probar se corrigió la spec: ningún rol solo ve las seis pantallas de Reportes (el dueño no tiene "Reportes de
  compras" y el auditor no tiene "Informe de compras"); el criterio quedó "las pantallas que el rol puede ver".
- En la corrida completa, el primer `beforeEach` de `shell.spec.ts` pasaba de 10 s al compilar el marco con toda la
  suite en paralelo (solo, pasa). Ese paso tiene ahora su propio límite de 30 s, con el motivo en un comentario.
