# Tasks 018

- [ ] T1: Escribir `revision-flujo-cajas.md` (recorrido, roles, endpoints, problemas y qué spec los resuelve) y revisarlo
  con el equipo — escrito; falta la revisión del equipo
- [x] T2: Separar los formularios de "Mi turno" en componentes hijos (`movimiento-caja`, `abono-turno`, `cierre-turno`) sin
  cambiar su comportamiento; mover sus pruebas
- [x] T3: Estados de la pantalla (sin turno, abierto, recién cerrado) y encabezado con "Cerrar turno" e "Ir al punto de
  venta"; pruebas
- [x] T4: Selector de acción que muestra un solo formulario, recordado en la URL; pruebas
- [x] T5: Ajustar el e2e `venta-completa` al selector
- [ ] T6: `npm test`, `npm run build` sin advertencias y revisión en Docker con el cajero — `npm test` en verde (266) y
  `tsc` sin errores; falta el build (Google Fonts sin red en este equipo) y la revisión en Docker
- [ ] T7: Verificar los criterios de aceptación y actualizar el índice de specs

Notas (2026-11-24):
- Lo que la reunión vio como "crear turno con el turno abierto" en realidad salía después de cerrar: la pantalla ya
  separaba los casos, pero el resultado del cierre y el formulario de abrir quedaban muy cerca. Ahora hay estados claros
  y el formulario solo aparece al pulsar "Abrir otro turno".
- El botón del formulario de cierre pasó a llamarse "Confirmar cierre" para no confundirse con "Cerrar turno" del
  encabezado (que solo abre el formulario). El e2e se ajustó.
- Los movimientos del turno ahora se ven en su propia tarjeta debajo del formulario elegido.
