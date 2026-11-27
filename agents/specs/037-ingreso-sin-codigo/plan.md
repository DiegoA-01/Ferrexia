# Plan 037

Rama `feature/ingreso-sin-codigo`, sacada de `develop` (`a3a7147`, con la 035) el 2026-11-27. Migración **V58**.

## Lo que hay hoy (revisado en el código el 2026-11-27)
- `LoginRequest {empresa, correo, password}`; `AuthController` resuelve la empresa por `codigo_publico` (sin empresa) y
  corre `AuthService.login` dentro de `EmpresaContexto.como`. La recuperación de clave también pide `empresa`.
- `usuario`: `UNIQUE (empresa_id, correo)` (V17). Validador `@CorreoUnico` (usuario/validation) revisa dentro de la empresa.
- Usuarios internos del super administrador (035): un usuario por empresa del grupo con el mismo correo
  (`usuario_grupo_empresa`); `usuarioRepository.contarVinculosDeGrupo`.
- Frontend: `features/ingreso/ingreso.html` y `recuperar.*` piden el código; `sesion.service.ts` lo manda; la consola de
  plataforma (`features/plataforma/empresas.*`, `grupos.*`) y `features/grupo/grupo-inicio.*` lo muestran.
- `scripts/sembrar_demo.py` y el e2e (`e2e/pruebas/*.spec.ts`, credenciales con `empresa`) inician sesión con el código.

## Backend
1. **V58:** comprobar que no haya correos repetidos entre empresas fuera de los usuarios internos de grupo (si los hay, la
   migración falla con un mensaje claro). Hacer cumplir la unicidad global con una columna generada o un índice que
   ignore a los usuarios internos de grupo; por ejemplo una columna `correo_login` (NULL para los internos de grupo, el
   correo en minúsculas para el resto) con `UNIQUE`. Mantener `UNIQUE (empresa_id, correo)`.
2. **Login:** `LoginRequest {correo, password}` (sin `empresa`). Buscar el usuario por correo **sin empresa** (consulta
   explícita, como `FirmaEnlace` o el registro: JDBC o `EmpresaContexto.sinEmpresa`), excluyendo a los internos de grupo,
   y luego seguir igual dentro de `EmpresaContexto.como(empresaDelUsuario)`: clave, bloqueo, suscripción, MFA, auditoría.
   Correo inexistente: el mismo 401 que una clave mala (y el mismo tiempo aproximado: comparar contra un hash de relleno).
   El desafío de MFA ya lleva `emp`: no cambia.
3. **Recuperación de clave:** `{correo}` solo; misma búsqueda sin empresa; respuesta siempre igual (no revela si existe).
4. **Correo único global:** `@CorreoUnico` y el alta/edición de usuarios (incluido el admin que crea la plataforma y los
   usuarios de proveedor si aplica) revisan el correo en todas las empresas, ignorando a los internos de grupo: 409 o 400
   "ese correo ya está registrado". El vínculo del super administrador (035) sigue pudiendo crear su usuario interno.
5. **Consola de plataforma:** el alta de empresa ya no exige ni devuelve el código para mostrarlo (se sigue generando por
   dentro, spec 020).
6. Rate limit y auditoría igual que hoy.

## Frontend
- `/ingreso`: sin el campo "Código de empresa" (correo y contraseña o PIN); `SesionService.ingresar` sin `empresa`.
- `/recuperar`: solo correo.
- Consola de plataforma y zona del grupo: quitar el código de las tablas, tarjetas y avisos ("código de ingreso").
- Textos de ayuda que mencionen el código.

## Otros
- `scripts/sembrar_demo.py`: login sin código; el archivo de credenciales ya no muestra el código para entrar.
- e2e: login sin código.
- `CLAUDE.md` › Decisiones vigentes y `.claude/skills/supply-core/references/`: actualizar donde diga "login con código de
  empresa" (solo esa frase).

## Pruebas
- Login con correo y clave de A entra a A; correo inexistente y clave mala dan el mismo 401; bloqueo y MFA siguen.
- Recuperación con solo el correo.
- Crear un usuario en B con un correo de A: rechazado; el usuario interno del super administrador sí se crea.
- `AislamientoEmpresasTest`: ajustar los logins existentes al nuevo `LoginRequest`; el correo de A nunca entra a B.
- Frontend: ingreso y recuperar sin código; plataforma y grupo sin mostrar el código.
- Migración: con correos repetidos falla con un mensaje claro (si se puede probar en `AislamientoEmpresasTest` o con
  una prueba de la regla).

Verificación final: `./mvnw verify`, `npm test`, tipos regenerados y revisión en Docker (resembrar la demo).
