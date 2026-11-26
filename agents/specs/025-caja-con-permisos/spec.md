# 025 - Caja con permisos: el dueño vende, cajas por persona y gastos sin PIN

**Estado:** En construcción (2026-11-25) · **Rama:** `feature/cierre-por-denominacion` · **Depende de:** 022 ·
**Responsable:** Persona 1 · **Puntos de la reunión 2:** 18, 51, 54 · **Migración:** V47 · **Decisión:** D15 (tomada)

## Objetivo
Con los permisos de la spec 022, ajustar la caja a cómo trabaja el negocio: el dueño también vende, cada persona usa
solo las cajas que le asignan, y quien tiene permiso registra retiros y gastos sin pedir el PIN del supervisor.

## Historias
- Como dueño, quiero abrir turno y vender cuando haga falta.
- Como administrador, quiero decidir quién puede usar la caja 1 y quién la caja 2.
- Como cajero de confianza, quiero registrar un retiro o un gasto sin esperar al supervisor.

## Comportamiento
- **El dueño vende:** la plantilla Dueño incluye abrir turno y vender. El dueño sigue las mismas reglas de caja que
  cualquier cajero (un turno por caja, cierre y diferencias).
- **Cajas por persona:** en la ficha del trabajador (o en la caja) se eligen las cajas que puede usar. En "Mi turno"
  solo salen esas cajas. Sin cajas asignadas, la persona puede usar todas (así nadie se queda sin vender el día del
  cambio).
- **Retiros y gastos (D15):** si la persona tiene el permiso "registrar retiro" o "registrar gasto", no se pide PIN. Si
  no lo tiene, se sigue pidiendo el PIN de un supervisor como hoy. En los dos casos queda en el libro de caja quién lo
  hizo y, si hubo, quién lo autorizó.

## Criterios de aceptación
- [ ] El dueño puede abrir turno y vender.
- [ ] En "Mi turno" cada persona solo ve las cajas que tiene asignadas.
- [ ] Con permiso, el retiro y el gasto no piden PIN; sin permiso, sí.
- [ ] Todo retiro y gasto sigue quedando en el libro de caja y en la auditoría.
- [ ] Prueba de fuga entre empresas para la asignación de cajas.

## Fuera de alcance
- Cambiar el PIN de otras autorizaciones (descuentos, anulaciones) salvo que el equipo lo pida.
