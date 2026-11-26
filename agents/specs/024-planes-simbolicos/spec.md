# 024 - Planes simbólicos

**Estado:** En construcción (2026-11-25, V46) · **Rama:** `feature/cierre-por-denominacion` · **Depende de:** SaaS 2 ·
**Responsable:** Persona 2 · **Puntos de la reunión 2:** 1, 4, 7 · **Decisión:** D11 (tomada: sin planes, módulos por
empresa)

> **Cambio por la decisión D11 (2026-11-25):** el equipo decidió quitar los planes del todo, no dejarlos como etiqueta.
> Al crear una empresa, el dueño de la plataforma marca en un panel los módulos que esa empresa necesita; después los
> prende o apaga uno por uno en su detalle. Las empresas que ya existían quedan con todos los módulos. Donde abajo dice
> "etiqueta" o "excepciones", manda esta nota.

## Objetivo
En la reunión se decidió que los planes no deben limitar lo que una empresa puede usar: ventas entra también en el
básico y los planes pasan a ser solo un nombre. Esta spec deja de bloquear módulos por plan y simplifica la consola del
dueño.

## Historias
- Como empresa con cualquier plan, quiero usar todos los módulos, incluido Ventas.
- Como dueño de la plataforma, quiero ver el plan de cada empresa solo como referencia, sin administrar módulos por
  plan.

## Comportamiento
- **Todos los módulos activos (D11):** todas las empresas tienen todos los módulos, sin importar su plan. Ninguna
  pantalla sale con candado por plan.
- El plan queda como **etiqueta** en la empresa (Básico, Profesional, Empresarial) que se ve en la consola.
- En la consola se quitan crear, editar módulos y eliminar planes. Las excepciones de módulo por empresa se conservan
  solo si el equipo lo decide en D11; si no, también se quitan.
- La suscripción (prueba, activa, morosa, suspendida, cancelada) sigue funcionando igual: una empresa suspendida sigue
  sin poder entrar.
- Las tablas de planes se conservan, por si más adelante se cobra por plan (SaaS 4).

## Criterios de aceptación
- [ ] Una empresa con plan Básico usa Ventas y los demás módulos.
- [ ] La consola no permite crear ni eliminar planes ni cambiar sus módulos.
- [ ] La suspensión y la mora siguen bloqueando como antes.
- [ ] Ningún dato de planes se borra.

## Fuera de alcance
- Cobro de la suscripción (SaaS 4).
