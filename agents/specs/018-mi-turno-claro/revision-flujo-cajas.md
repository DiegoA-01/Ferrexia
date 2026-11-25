# Revisión del flujo de cajas y turnos (punto 52 de la reunión 2)

Revisado en el código el 2026-11-24 (`develop` con las specs 003, 005, 006, 007, 009, 010, 015, 016 y 017). Sirve de base
para las specs 021, 023, 025 y 029.

## 1. Recorrido actual

| # | Paso | Quién (rol hoy) | Pantalla | Endpoint | Reglas que ya existen |
|---|---|---|---|---|---|
| 1 | Configurar cajas (sede, bodega, prefijo, tope de efectivo) y la tolerancia | ADMIN_SISTEMA | Cajas y turnos | `POST/PUT /api/cajas`, `PUT /api/cajas/configuracion` | Una caja, una bodega (D7). Tolerancia por defecto $2.000 (D6). |
| 2 | Fijar el PIN de supervisor | SUPERVISOR_VENTAS, ADMIN_SISTEMA | Perfil | `PUT /api/autorizaciones/mi-pin` | 6 dígitos, se bloquea 15 min a los 5 fallos. |
| 3 | Abrir turno con base | CAJERO, SUPERVISOR_VENTAS | Mi turno | `POST /api/turnos` | Un turno por caja y por persona; las cajas ocupadas salen con quién y desde cuándo (015). |
| 4 | Vender | CAJERO, SUPERVISOR_VENTAS (VENDEDOR arma pedidos) | Punto de venta | `POST /api/ventas` | Sin turno abierto el POS lleva a Mi turno. Método de pago obligatorio (016). |
| 5 | Retiro a la bóveda, gasto menor o ingreso | CAJERO (con PIN de supervisor) | Mi turno | `POST /api/autorizaciones` + `POST /api/turnos/{id}/movimientos` | Siempre con autorización de un solo uso. Aviso `debeRetirar` al pasar el tope. |
| 6 | Abono de cliente (cartera) | CAJERO | Mi turno | `POST /api/abonos` | Entra a la caja del turno; imprime recibo. |
| 7 | Anular una venta | CAJERO con PIN, SUPERVISOR_VENTAS | Ventas | `POST /api/ventas/{id}/anular` | Solo dentro del mismo turno abierto (006). |
| 8 | Cerrar a ciegas | CAJERO, SUPERVISOR_VENTAS | Mi turno | `POST /api/turnos/{id}/cerrar` | El cajero escribe lo contado por medio; el esperado y la diferencia aparecen después de guardar. |
| 9 | Aprobar la diferencia | SUPERVISOR_VENTAS, ADMIN_SISTEMA | Cajas y turnos | `POST /api/turnos/{id}/aprobar` | Solo si pasa la tolerancia; nadie aprueba su propio turno. |
| 10 | Cuadre del día | Automático (job) / ADMIN_SISTEMA | Reportes de ventas | `GET /api/reportes/cuadre` | Spec 009. |

## 2. Lo que se vio en la reunión y qué spec lo resuelve

| Punto | Problema | Spec |
|---|---|---|
| 49 | Con el turno abierto no hay un botón claro para cerrar; después de cerrar parece que se "crea" otro turno | 018 |
| 55 | "Mi turno" muestra todas las acciones a la vez | 018 |
| 44 | No se ven las facturas vendidas en el turno | 021 |
| 45 | Ingresos y egresos sin categoría | 021 |
| 46 | Facturas del día sin paginación, reimprimir, editar ni estado | 021 (lista y reimprimir), 029 (editar) |
| 53 | Ver lo que debería haber antes de cerrar | 021 (decisión D20) |
| 50 | Contar billetes y monedas al cerrar; ventas por turno | 023 |
| 51 | Decidir quién usa la caja 1 o la 2 | 025 |
| 54 | Retiro y gasto piden PIN aunque la persona sea de confianza | 025 (decisión D15) |
| 18 | El dueño no puede vender | 025 |
| 21, 43 | Anular una factura de otro turno y cambiar el medio de pago | 029 |

## 3. Preguntas abiertas para el equipo
- **D15:** ¿retiro, gasto e ingreso sin PIN para quien tenga el permiso? (recomendado: sí, con auditoría).
- **D20:** ¿el cajero ve lo esperado antes de contar? (recomendado: no; lo ve al cerrar, y quien tenga el permiso "ver
  esperado" lo ve siempre).
- Si el dueño es la única persona que aprueba diferencias y abre turno, su propia diferencia queda pendiente: ¿se acepta o
  se permite que la apruebe el auditor?
