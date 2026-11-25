# Plan 021

Rama `feature/mi-turno-movimientos`, sacada de `develop` actualizado **después de mezclar la 018** (usa sus componentes
hijos). Migración propuesta **V42**. Decisión **D20** pendiente (lo esperado antes de cerrar).

## Lo que hay hoy (revisado en el código)
- **Ventas del turno:** `GET /api/ventas` ya filtra por `turnoId`, pagina y el tiquete se reimprime con
  `GET /api/ventas/{id}/tiquete`. Pero `VentaService.buscar` limita al cajero: solo puede buscar **por consecutivo**
  (`AccesoDenegadoException "El cajero busca sus ventas por consecutivo"`), así que hoy no puede listar su turno.
- **Movimientos de caja:** `TipoMovimientoCaja` = BASE, VENTA, DEVOLUCION, ANULACION, RETIRO, GASTO, INGRESO.
  `MovimientoCajaRequest(tipo, monto, motivo, soporte, autorizacion)`: retiro, gasto e ingreso piden siempre autorización de
  supervisor (`TurnoService` línea ~171). `movimiento_caja` no tiene categoría.
- **Esperado:** `CalculoCaja` calcula base + ventas en efectivo + abonos − devoluciones − retiros − gastos (más ingresos).
  El cajero no lo ve con el turno abierto (cierre ciego, spec 003); lo ve en la respuesta **después** de cerrar.

## Backend
1. **Facturas del turno:** en `VentaService.buscar`, si quien consulta es cajero y pide `turnoId` de **su turno abierto**,
   se permite la lista (paginada, 20 por página). Cualquier otro `turnoId` sigue rechazado para el cajero. La respuesta
   (`VentaResumen`) ya trae número, hora, cliente, total y estado; se suma `medios` (lista corta de medios de pago).
2. **Categorías de ingreso y egreso (V42):**
   - Tabla `categoria_movimiento_caja` (`id`, `empresa_id`, `tipo` ENUM('INGRESO','EGRESO'), `nombre` VARCHAR(80),
     `activa`, `creado_en`; único por empresa, tipo y nombre) con `@TenantId`.
   - `movimiento_caja.categoria_id BIGINT NULL` con FK.
   - Siembra por empresa (`INSERT … SELECT` desde `empresa`): egresos "Papelería", "Aseo", "Domicilio", "Otro"; ingresos
     "Base adicional", "Otro". Las empresas nuevas reciben las mismas en `EmpresaAltaService`.
   - `CategoriaMovimientoCajaController` en `/api/cajas/categorias-movimiento`: listar (cajero, supervisor, admin, auditor),
     crear, renombrar y activar/desactivar (admin y supervisor). No se borran; desactivar una usada solo la oculta.
   - `MovimientoCajaRequest` suma `categoriaId`: obligatorio para GASTO (egreso) e INGRESO, opcional para RETIRO. Debe ser
     de la empresa, activa y del tipo correcto (409 `CATEGORIA_INVALIDA`).
   - `MovimientoCajaResponse` suma `categoria`.
3. **Lo esperado (según D20):**
   - Opción A (recomendada): se mantiene el cierre ciego. Al cerrar, la respuesta ya trae esperado y diferencia; la pantalla
     los muestra en un resumen claro. Además, quien tenga el permiso "ver esperado de caja" (spec 022; mientras tanto
     SUPERVISOR_VENTAS y ADMIN_SISTEMA) puede consultar `GET /api/turnos/{id}/esperado` en cualquier momento.
   - Opción B: el cajero ve lo esperado antes de contar (`GET /api/turnos/{id}/esperado` también para el dueño del turno).
     Se pierde el cierre ciego.
   - Se implementa la que apruebe el equipo; el endpoint es el mismo y solo cambia quién lo puede llamar.

## Frontend
- **Mi turno › Facturas del turno** (acción nueva del selector de la 018): tabla paginada con número, hora, cliente, total,
  medios y estado; por fila **Reimprimir** (abre el tiquete de 80 mm existente), **Detalle** (diálogo con líneas y pagos) y
  **Editar** (desactivado hasta la spec 029, con texto "Disponible con la spec 029").
- **Registrar ingreso / gasto:** select de categoría (solo activas del tipo), monto con `scCifra`, motivo, foto opcional
  del soporte (ya existe en gastos).
- **Configuración › Caja › Categorías de ingresos y egresos:** lista con crear, renombrar y activar/desactivar.
- **Cierre:** según D20 (resumen después de cerrar, o tarjeta "Lo que debería haber" antes de contar).

## Pruebas
- `VentaServiceTest`: el cajero lista su turno abierto; no lista un turno ajeno ni uno cerrado (403).
- `CategoriaMovimientoCajaServiceTest`: crear, nombre repetido 409, desactivar una usada la oculta sin borrarla.
- `TurnoService` (prueba nueva o en `TurnoServiceTest`): gasto sin categoría 400/409, con categoría de otro tipo 409,
  ingreso con categoría suma al esperado.
- `AislamientoEmpresasTest`: categorías y movimientos de A no se ven en B; la siembra crea las categorías por empresa.
- Frontend: `mi-turno` (facturas paginadas, reimprimir llama al tiquete), formulario de gasto con categoría, pantalla de
  categorías.

Verificación final: `./mvnw verify`, `npm test`, `npm run build`, e2e del CI y revisión en Docker con el cajero (vender,
ver facturas, gasto con categoría, cerrar) y el supervisor (categorías, esperado).
