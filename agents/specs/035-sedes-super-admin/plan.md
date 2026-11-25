# Plan 035

Rama `feature/sedes-super-admin`, sacada de `develop` actualizado **después de la 020 y la 022**. Migración propuesta
**V54**. **No se construye hasta aclarar D12** (las tres preguntas de la spec); este plan supone la respuesta propuesta:
cada sede es una empresa aparte, con sus propios productos y precios, agrupadas bajo un mismo dueño, sin traslados entre
empresas.

## Lo que hay hoy (revisado en el código)
- Aislamiento por empresa con `@TenantId` en todas las entidades de negocio; la empresa sale del claim `emp` del JWT y el
  `AuthFilter` la fija en `EmpresaContexto`. Sin empresa no se abre sesión de base de datos.
- `usuario.empresa_id` es NOT NULL y no se puede cambiar: **un usuario pertenece a una sola empresa**. El correo es único
  por empresa (el mismo correo puede existir en dos empresas como dos usuarios distintos).
- La consola de plataforma (`usuario_plataforma`, MFA obligatorio) ya trabaja sin empresa y puede recorrer empresas.
- Dentro de una empresa ya hay sedes, bodegas por sede, inventario por sede (V27) y cajas por sede.

## Diseño
1. **Grupo de empresas (V54):** tabla `grupo_empresa (id, nombre, creado_en)` y `empresa.grupo_id BIGINT NULL`. Lo crea y
   arma el dueño de la plataforma desde la consola.
2. **Super administrador del grupo:** tabla `usuario_grupo (id, grupo_id, correo, nombre, hash_password, mfa_secreto,
   mfa_activo, …)` con login propio (`POST /api/grupo/auth/login` + MFA obligatorio, mismo modelo que la consola de
   plataforma) y tabla `usuario_grupo_empresa (usuario_grupo_id, empresa_id, usuario_id)` que vincula al super
   administrador con **un usuario Dueño en cada empresa** del grupo.
3. **Cambiar de empresa:** `POST /api/grupo/entrar/{empresaId}` emite un JWT normal de esa empresa para el usuario vinculado
   (con su `emp`, sus permisos y su `sv`). Cambiar de empresa = pedir otro token; no se mezclan datos porque cada token
   sigue atado a una sola empresa y todo el aislamiento actual sigue igual.
4. **Tablero del grupo:** `GET /api/grupo/tablero?desde&hasta` (token de grupo, no de empresa) recorre las empresas del
   grupo con `EmpresaContexto.como(empresa, …)` y devuelve ventas, compras e inventario valorizado por empresa y sumados.
   Solo lectura.
5. Auditoría: entrar a una empresa queda en la auditoría de esa empresa (`GRUPO_ENTRAR`) y en una auditoría del grupo.

## Frontend
- **Consola de plataforma:** crear grupo, agregar empresas al grupo y crear el super administrador (con su usuario Dueño
  en cada empresa).
- **Ingreso del grupo** (`/grupo/ingreso`) y **selector de empresa** (tarjetas con el nombre de cada empresa). Dentro de una
  empresa, en la barra superior, "Cambiar de empresa".
- **Tablero del grupo** con totales y una fila por empresa.

## Pruebas
- Login de grupo con MFA; sin MFA no entra.
- Entrar a una empresa del grupo da un token de esa empresa; a una que no es del grupo, 403.
- Con el token de la empresa A no se ve nada de B aunque sean del mismo grupo (todas las pruebas de fuga actuales siguen
  pasando, y se suma una con dos empresas del mismo grupo).
- El tablero del grupo suma bien y no incluye empresas de otro grupo.
- Un usuario normal no puede llamar a `/api/grupo/**`, y un token de grupo no entra a `/api/**` de una empresa.

Verificación final: `./mvnw verify` (todas las pruebas de fuga), `npm test`, `npm run build` y revisión en Docker con dos
empresas en un grupo.

## Riesgos
- Toca la parte más delicada (autenticación y aislamiento). Se hace después de la 022 y con revisión de seguridad
  (skill de pruebas de seguridad) antes de mezclar.
- Si D12 responde que basta con separar inventario y caja por sede dentro de la misma empresa, esta spec se reemplaza por
  una mucho más pequeña (permisos por sede) y este plan se descarta.
