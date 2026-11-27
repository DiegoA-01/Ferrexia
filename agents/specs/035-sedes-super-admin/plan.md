# Plan 035

Rama `feature/sedes-super-admin`, sacada de `develop` (`a43242f`, con la 034) el 2026-11-27. Migración **V57**. D12
tomada (ver spec). Toca autenticación y aislamiento: revisión de seguridad del diff antes de mezclar.

## Lo que hay hoy (revisado en el código el 2026-11-27)
- No existe nada de "grupo". Cada `usuario` pertenece a una empresa (`@TenantId`; correo único por empresa, V17).
- Login de empresa: `AuthController` resuelve la empresa por `codigo_publico` y corre `AuthService.login` dentro de
  `EmpresaContexto.como`. El JWT de empresa lleva `emp` y `sv` (`JwtUtil`).
- Consola de plataforma: `usuario_plataforma` con MFA TOTP obligatorio, JWT propio sin `emp` (`validarPlataforma`),
  `PlataformaAuthService`, `TotpService`, alta de empresas en `EmpresaAltaService` (ya siembra un usuario en la empresa).
- `AuthFilter`: ramas para registro/enlace, rutas públicas, `/api/plataforma/` y el resto con token de empresa.
- `EmpresaContexto.como(empresaId, ...)` corre código dentro de otra empresa (jobs, tablero).
- Inventario: `MovimientoInventarioService` con kardex y costo promedio (spec 007); `TipoMovimiento.TRASLADO` existe para
  traslados entre bodegas de la misma empresa.

## Migración V57
- `grupo_empresa (id, nombre, activo, creado_en)` — global, de la plataforma.
- `empresa.grupo_id BIGINT NULL` (FK).
- `usuario_grupo (id, grupo_id, nombre, correo UNIQUE, hash_password, activo, version_sesion, mfa_secreto, mfa_activo,
  mfa_ultimo_paso, intentos_fallidos, bloqueado_hasta, creado_en)` — global, como `usuario_plataforma`.
- `usuario_grupo_empresa (usuario_grupo_id, empresa_id, usuario_id)` — vínculo del super administrador con su usuario
  Dueño en cada empresa del grupo (PK `usuario_grupo_id, empresa_id`).
- `traslado_grupo (id, grupo_id, consecutivo, empresa_origen_id, empresa_destino_id, bodega_origen_id,
  bodega_destino_id NULL, estado ENUM('EN_TRANSITO','RECIBIDO','ANULADO'), nota, enviado_por_usuario_id, enviado_en,
  recibido_por_usuario_id, recibido_en, anulado_por_usuario_id, anulado_en)` y `traslado_grupo_linea (id, traslado_id,
  producto_origen_id, producto_destino_id, cantidad, cantidad_recibida NULL, costo_unitario)`. Tablas entre dos empresas:
  **no** usan `@TenantId`; todo acceso pasa por el servicio y exige que la empresa del contexto sea la origen o la destino.
- Tipos de movimiento nuevos (si `movimiento_inventario.tipo` es ENUM, ampliarlo): `SALIDA_TRASLADO_GRUPO`,
  `ENTRADA_TRASLADO_GRUPO`.
- Permisos `TRASLADO_GRUPO_ENVIAR` y `TRASLADO_GRUPO_RECIBIR` para `ADMIN_SISTEMA`, `ADMINISTRADOR` y `ALMACENISTA`.
- Consecutivo `TG` del traslado.

## Backend
1. **Grupo en la plataforma** (`/api/plataforma/grupos`, solo token de plataforma): crear y listar grupos; asignar o
   quitar una empresa (`PUT /api/plataforma/empresas/{id}/grupo`); crear el super administrador del grupo (nombre, correo,
   clave fuerte de 12+). Al crearlo, y cada vez que una empresa entra al grupo, se crea en cada empresa un usuario con el
   rol `ADMIN_SISTEMA` (correo del super administrador, clave aleatoria inutilizable: nadie entra con ella por el login
   normal) y se guarda el vínculo. Al quitar una empresa del grupo o desactivar al super administrador, esos usuarios se
   desactivan y su `version_sesion` sube (sesiones cortadas). La plataforma sigue sin leer datos de negocio.
2. **Login del grupo** (`/api/grupo/auth/**`, público con límite de peticiones): correo y clave, y **MFA TOTP
   obligatorio** (primer ingreso: configurar; luego: código), igual que la plataforma (bloqueo por intentos incluido).
   JWT propio con el claim `grp` (sin `emp` ni `plataforma`) y versión de sesión; un token de grupo no sirve en la API de
   una empresa ni en la de plataforma, y al revés.
3. **`AuthFilter`:** rama nueva para `/api/grupo/` (salvo `auth`): exige token de grupo, lo deja en la petición
   (`GrupoActual`) y trabaja sin empresa salvo donde el servicio use `EmpresaContexto.como`.
4. **Empresas y entrada:** `GET /api/grupo/empresas` (las del grupo, activas). `POST /api/grupo/entrar/{empresaId}`:
   verifica que la empresa sea del grupo del token y que exista el vínculo activo, y emite una sesión normal de esa
   empresa (JWT + refresh) para el usuario Dueño vinculado. Auditoría `GRUPO_ENTRAR` en la empresa. Una empresa de otro
   grupo o sin vínculo: 404.
5. **Tablero:** `GET /api/grupo/tablero?desde&hasta`: por cada empresa del grupo, dentro de `EmpresaContexto.como`,
   ventas netas (reutilizar `VentaReporteService.deEmpresa`), compras del periodo (órdenes emitidas, no canceladas ni
   reemplazadas) e inventario valorizado (reutilizar el cálculo existente); total del grupo. Java suma y redondea.
6. **Traslados** (`/api/traslados-grupo`, token de empresa, `@RequierePermiso`):
   - `GET /destinos`: empresas del mismo grupo (id y nombre), sin datos de negocio.
   - `POST /` (TRASLADO_GRUPO_ENVIAR): destino, bodega de origen, líneas (producto origen y cantidad), nota. Valida que la
     destino sea del mismo grupo y distinta; empareja cada producto por código (o código de barras) en la destino (lo lee
     con `EmpresaContexto.como(destino)`); si falta alguno, 409 `PRODUCTOS_SIN_PAREJA` con la lista; sin stock suficiente,
     409 como las salidas normales. Crea `SALIDA_TRASLADO_GRUPO` en la origen con el costo promedio y deja `EN_TRANSITO`.
   - `POST /{id}/recibir` (TRASLADO_GRUPO_RECIBIR, solo desde la destino): bodega destino y cantidad recibida por línea
     (≤ enviada); crea `ENTRADA_TRASLADO_GRUPO` en la destino al costo de la línea (actualiza el costo promedio de la
     destino); lo no recibido queda como faltante en el traslado. Estado `RECIBIDO`.
   - `POST /{id}/anular` (TRASLADO_GRUPO_ENVIAR, solo desde la origen y en tránsito): devuelve el stock a la origen.
   - `GET /` (enviados y recibidos de la empresa del contexto) y `GET /{id}`. Una empresa que no es origen ni destino: 404.
   - Avisos: al enviar, a quien tenga el permiso de recibir en la destino.
7. Auditoría de todo lo anterior; Javadoc en todo método.

## Frontend
- **Plataforma:** pantalla "Grupos": crear grupo, asignar empresas, crear el super administrador.
- **Zona del grupo:** `/grupo/ingreso` (clave y segundo factor) y `/grupo` con las empresas (botón "Entrar") y el tablero
  del grupo. Al entrar a una empresa se usa la sesión normal de `/app`; la barra muestra "Cambiar de empresa" si la sesión
  vino del grupo (el token del grupo se guarda como el de la plataforma).
- **Inventario › Traslados entre empresas:** lista de enviados y recibidos, "Nuevo traslado" (destino, bodega, productos
  con cantidad; muestra los que no tienen pareja), "Recibir" (bodega y cantidades) y "Anular". Opción en el menú según los
  permisos.

## Pruebas
- Login del grupo con MFA; token de grupo rechazado en `/api/...` de empresa y en `/api/plataforma/...`, y al revés.
- Entrar a una empresa del grupo emite sesión de esa empresa; a una de otro grupo: 404.
- Usuario normal de A no ve nada de B aunque estén en el mismo grupo.
- Tablero: la suma del grupo = suma de las empresas.
- Traslado: baja el stock en la origen al enviar, sube en la destino al recibir (con faltante), kardex en las dos y costo
  promedio; anular devuelve el stock; sin pareja de código: 409; destino de otro grupo: 404.
- `AislamientoEmpresasTest`: fuga entre empresas del mismo grupo y de grupos distintos (empresas, tablero, traslados).
- Frontend: ingreso del grupo, selector, cambiar de empresa, tablero, pantalla de traslados.

Verificación final: `./mvnw verify`, `npm test`, tipos regenerados, revisión de seguridad del diff y revisión en Docker
(dos empresas en un grupo y una fuera).
