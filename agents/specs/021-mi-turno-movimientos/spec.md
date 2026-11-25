# 021 - "Mi turno": facturas del turno, ingresos y egresos

**Estado:** Aprobada · **Rama:** `feature/mi-turno-movimientos` · **Depende de:** 018 · **Responsable:** Persona 1 ·
**Puntos de la reunión 2:** 44, 45, 46, 53 · **Migración:** V42 · **Decisión:** D20 (tomada: opción A)

## Objetivo
Que el cajero maneje todo su turno desde "Mi turno": ver lo que vendió, registrar ingresos y egresos con una categoría,
y saber qué debería tener antes de cerrar.

## Historias
- Como cajero, quiero ver las facturas que vendí en mi turno, reimprimirlas y ver su detalle.
- Como cajero, quiero registrar ingresos y egresos de mi caja eligiendo una categoría (por ejemplo "Pago a domiciliario"
  o "Compra de papelería").
- Como administrador, quiero crear las categorías de ingresos y egresos de mi empresa.
- Como cajero, antes de cerrar quiero saber cuánto debería haber en la caja.

## Comportamiento
- **Facturas del turno:** en "Mi turno" hay una lista de las facturas del turno con paginación (20 por página), número,
  hora, cliente, total, método de pago y estado. Al final de cada fila: **reimprimir**, **ver detalle** y **editar**
  (lo que se pueda editar lo define la spec 029). Cuando exista la factura electrónica, la fila muestra también su
  estado ante la DIAN.
- **Ingresos y egresos:** se registran desde el menú de acciones (spec 018) con monto, categoría, motivo y foto
  opcional del soporte. Quedan en el libro de caja y suman o restan en lo esperado.
- **Categorías:** en Configuración › Caja se crean, renombran y desactivan categorías de ingreso y de egreso. Vienen
  algunas por defecto. No se borran si ya se usaron.
- **Lo esperado antes de cerrar (D20, tomada: opción A):** el cierre sigue ciego. El cajero cuenta primero, y
  después de guardar su conteo ve lo esperado y la diferencia en un resumen. Quien tenga el permiso "ver esperado de
  caja" lo ve en todo momento (`GET /api/turnos/{id}/esperado`); hasta la spec 022 ese permiso lo tienen
  SUPERVISOR_VENTAS y ADMIN_SISTEMA.

## Criterios de aceptación
- [x] "Mi turno" muestra las facturas del turno con paginación, reimpresión y detalle.
- [x] Un ingreso o egreso se registra con su categoría y cambia lo esperado.
- [x] Las categorías se administran en Configuración y no se borran si se usaron (en Cajas y turnos › Cajas; no hay
  borrado: se desactivan).
- [x] Lo esperado se muestra según la decisión D20 (cierre ciego; el supervisor lo ve en el detalle del turno abierto).
- [x] Prueba de fuga entre empresas para las categorías y los movimientos.

## Fuera de alcance
- Conteo por billetes y monedas (spec 023).
- Anular o cambiar el método de pago de una factura (spec 029).
- Factura electrónica DIAN.
