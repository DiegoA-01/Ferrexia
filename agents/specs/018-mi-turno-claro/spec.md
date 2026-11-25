# 018 - "Mi turno" más claro y revisión del flujo de cajas

**Estado:** En construcción (aprobada por el equipo el 2026-11-24) · **Rama:** `feature/mi-turno-claro` · **Depende de:** 003, 015 · **Responsable:** Persona 1 ·
**Puntos de la reunión 2:** 49, 52, 55

## Objetivo
Hoy "Mi turno" muestra todas sus acciones a la vez y, con el turno abierto, sigue apareciendo la opción de crear turno.
Esta spec ordena la pantalla y deja escrita una revisión completa del flujo de cajas y turnos, que sirve de base para
las specs 021, 023 y 025.

## Historias
- Como cajero, con mi turno abierto quiero ver mi turno y no el formulario para abrir otro.
- Como cajero, quiero un botón claro para cerrar mi turno.
- Como cajero, quiero elegir la acción que voy a hacer (retiro, gasto, ingreso, cerrar) de un menú, sin ver todas a la
  vez.

## Comportamiento
- **Sin turno abierto:** sale solo el formulario de abrir turno (con las cajas libres y ocupadas de la spec 015).
- **Con turno abierto:** sale el resumen del turno (caja, desde qué hora, quién) y ya no aparece "crear turno". Hay un
  botón visible **Cerrar turno** que lleva al cierre.
- **Acciones en un menú:** las acciones del turno (registrar retiro, gasto, ingreso, ver facturas del turno, cerrar)
  salen en un menú desplegable; al elegir una se abre solo ese formulario.
- **Revisión del flujo (punto 52):** se escribe `agents/specs/018-mi-turno-claro/revision-flujo-cajas.md` con el
  recorrido actual (abrir, vender, retirar, gastar, cerrar, aprobar diferencia), quién hace cada paso, los problemas
  vistos en la reunión y qué spec los resuelve. Se revisa con el equipo antes de aprobar la 021.

## Criterios de aceptación
- [x] Con turno abierto no aparece la opción de abrir otro turno.
- [x] El botón Cerrar turno está visible con el turno abierto.
- [x] Las acciones del turno salen en un menú y solo se muestra el formulario elegido.
- [ ] La revisión del flujo de cajas queda escrita y revisada por el equipo. (Escrita en `revision-flujo-cajas.md`; falta
  la revisión del equipo.)

> Verificado el 2026-11-24 con pruebas: `mi-turno.spec.ts` (sin turno solo "Abrir turno"; con turno abierto no está el
> formulario de abrir y sí "Cerrar turno" e "Ir a vender"; sin acción no hay formulario; elegir gasto muestra solo ese;
> cambiar a cerrar lo reemplaza; el botón del encabezado elige el cierre; la acción queda en la URL y se recupera; sin
> cartera no hay abono; el aviso de tope lleva al retiro; recién cerrado, "Abrir otro turno" recién muestra el
> formulario) y `turno/turno-formularios.spec.ts` (retiro, abono y cierre ciego hacen lo mismo que antes). Frontend: 266
> pruebas en verde y `tsc` sin errores. Falta: el build de producción (en este equipo no descarga la fuente de Google
> Fonts; lo hace el CI), el e2e del CI y la revisión en Docker.

## Fuera de alcance
- Ver facturas del turno, ingresos/egresos con categoría y lo esperado antes de cerrar (spec 021).
- Conteo por billetes y monedas (spec 023).
- Cambiar el PIN por permisos (spec 025).
