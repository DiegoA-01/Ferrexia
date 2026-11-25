# Plan 025

Rama `feature/caja-con-permisos`, sacada de `develop` actualizado **después de la 022** (usa `@RequierePermiso` y los
permisos `CAJA_*`). Migración propuesta **V46**. Decisión **D15** debe estar tomada.

## Lo que hay hoy (revisado en el código)
- `TurnoController` exige a nivel de clase `CAJERO` o `SUPERVISOR_VENTAS`; `POST /api/ventas` igual. El dueño
  (`ADMIN_SISTEMA`) no puede abrir turno ni vender.
- `CajaService.listar` y "Mi turno" muestran **todas** las cajas activas (no hay asignación por persona; PROGRESS dice
  "el cajero ve todas las cajas activas").
- Retiro, gasto e ingreso siempre consumen una autorización de supervisor de un solo uso (`AutorizacionService.consumir`,
  acciones `RETIRO`, `GASTO`, `INGRESO`), que se pide con correo y PIN en `POST /api/autorizaciones`.

## Backend
1. **El dueño vende:** la plantilla Dueño (spec 022) incluye `CAJA_ABRIR_TURNO`, `CAJA_CERRAR_TURNO` y `VENTA_COBRAR`. Las
   reglas de caja no cambian: un turno por caja y por persona, cierre ciego, y nadie aprueba la diferencia de su propio
   turno (si el dueño es el único que aprueba, su diferencia queda pendiente con aviso; se anota como limitación).
2. **Cajas por persona (V46):** tabla `caja_usuario (caja_id, usuario_id, empresa_id)`, PK compuesta, con `@TenantId`.
   - `PUT /api/cajas/{id}/usuarios` (permiso `CAJA_CONFIGURAR`) reemplaza la lista.
   - `CajaService.listar` para quien abre turno: si la persona tiene cajas asignadas, solo esas; si no tiene ninguna, todas
     (regla de transición de la spec).
   - `TurnoService.abrir` rechaza una caja no asignada (403 `CAJA_NO_ASIGNADA`) cuando la persona tiene asignaciones.
3. **Retiro y gasto sin PIN (D15):** en `TurnoService.registrarMovimiento`, si quien registra tiene `CAJA_RETIRO` (o
   `CAJA_GASTO`, `CAJA_INGRESO`), la autorización deja de ser obligatoria y el movimiento queda con
   `autorizadoPorUsuarioId = null` y el motivo. Si no tiene el permiso, se sigue exigiendo el token de PIN como hoy.
   La auditoría guarda `sinPin=true` cuando aplica.

## Frontend
- **Mi turno:** el dueño ve "Mi turno" y "Punto de venta" (el menú los muestra por permiso). La lista de cajas respeta la
  asignación. El formulario de retiro y gasto no pide PIN si el usuario tiene el permiso (la pantalla lo sabe por
  `GET /api/auth/me`, que desde la 022 trae los permisos efectivos).
- **Configuración › Cajas:** en cada caja, "Quién puede usarla" con selección de usuarios activos que pueden abrir turno.
- **Usuarios:** en la ficha del trabajador, sus cajas asignadas (solo lectura, enlace a la caja).

## Pruebas
- `TurnoServiceTest`: el dueño abre turno; caja no asignada 403; sin asignaciones ve todas; retiro con permiso sin token;
  retiro sin permiso y sin token 409 como hoy.
- `CajaServiceTest`: listar filtra por asignación.
- `AislamientoEmpresasTest`: la asignación de cajas de A no afecta a B; un usuario de B no se puede asignar a una caja de A.
- Frontend: Mi turno con cajas asignadas; retiro sin PIN con permiso; configuración de cajas.

Verificación final: `./mvnw verify`, `npm test`, `npm run build`, e2e del CI y revisión en Docker con el dueño (abrir turno
y vender), un cajero con una caja asignada y un supervisor.
