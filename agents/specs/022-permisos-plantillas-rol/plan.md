# Plan 022

Rama `feature/permisos-plantillas-rol`, sacada de `develop` actualizado. Migraciones **V43** (permisos y plantillas,
Parte A) y **V44** (clave corta y roles propios, Parte B), las dos reservadas. Decisiones **D10** y **D18** tomadas tal como
estaban. Es la spec más grande de la tanda: se construye en **dos partes**, en dos PR.

## Ajustes al construir la Parte A (2026-11-25)
- **Catálogo:** en cada controlador, los endpoints con el mismo conjunto de roles forman un permiso: 97 permisos y 259
  pares rol-permiso (detalle en `permisos.md`). Así ningún código mezcla dos conjuntos de roles y la equivalencia es exacta.
- **Plantillas:** los 12 roles de siempre quedan como plantillas del sistema con el mismo código; siete toman el nombre de
  D18 (Dueño, Cajero, Vendedor, Supervisor de ventas, Bodega, Compras, Contador/Auditor). La plantilla **Administrador** se
  crea en la Parte B, con los roles propios.
- **Reglas dentro de los servicios (antes T8):** pasan a la Parte B. Mientras no existan roles propios, cada persona tiene
  solo roles de siempre y esas reglas deciden igual que hoy; cuando lleguen los roles propios, las reglas de "puede o no
  puede" deben mirar el permiso. Las de segregación (nadie aprueba lo suyo) no cambian.
- **Caché y versión de sesión:** `PermisosService` recuerda los permisos 30 segundos (`app.permisos.cache-segundos`). Subir
  la `version_sesion` al cambiar permisos queda para la Parte B, que es la que trae la API para cambiarlos.
- **Error:** sin el permiso, 403 con código `PERMISO_REQUERIDO` (antes `ACCESO_DENEGADO`; el frontend no lo distingue).

## Lo que hay hoy (revisado en el código)
- **Roles fijos:** 12 roles globales (`Roles.java`: ADMIN_SISTEMA, ADMIN_COMPRAS, SOLICITANTE, COMPRADOR, APROBADOR,
  ALMACENISTA, ADMINISTRATIVO, PROVEEDOR, AUDITOR, CAJERO, SUPERVISOR_VENTAS, VENDEDOR) en la tabla `rol` (sin
  `empresa_id`). `usuario_rol` une usuarios y roles.
- **Control de acceso:** `@RequiereRol` en **57 controladores** y `@UsuarioInterno`; los revisa `RolInterceptor` después de la
  suscripción y el módulo (`@RequiereModulo`). Además hay **unas 72 reglas** dentro de los servicios que miran
  `Roles.X` (segregación de funciones, "solo el cajero ve lo suyo", políticas de aprobación por rol, etc.).
- **Tablas que ya existen y no se usan:** `permiso (id, codigo, nombre)` y `rol_permiso (rol_id, permiso_id)` desde la V1;
  `Rol` ya mapea `rol_permiso`.
- **Políticas de aprobación** (`politica_aprobacion`) y la **delegación** guardan un **código de rol** como aprobador.
- **Claves:** `ReglasPassword` exige de 8 a 72 caracteres para todos; BCrypt 12. MFA obligatorio para ADMIN_SISTEMA.
  El PIN de supervisor (`usuario.pin_supervisor_hash`) es otra cosa y se mantiene.
- El JWT lleva los roles; la versión de sesión (`sv`) corta la sesión al cambiar la clave o desactivar al usuario.

## Diseño
**Parte A — Permisos sin cambiar el acceso de nadie**
1. **Catálogo de permisos** (global, sembrado en V43): un código por acción, agrupado por módulo. Se arma recorriendo los 57
   controladores: cada combinación endpoint + `@RequiereRol` se convierte en un permiso (por ejemplo `VENTA_COBRAR`,
   `VENTA_ANULAR`, `CAJA_ABRIR_TURNO`, `CAJA_RETIRO`, `CAJA_GASTO`, `CAJA_VER_ESPERADO`, `SOLICITUD_APROBAR`,
   `PRODUCTO_CREAR`, `USUARIO_RESTABLECER_CLAVE`…). Se estiman entre 80 y 120. La lista completa queda en
   `agents/specs/022-permisos-plantillas-rol/permisos.md` como parte de la Parte A.
2. **Plantillas por empresa:** `rol.empresa_id BIGINT NULL` (NULL = plantilla del sistema, de solo lectura) y
   `rol.es_plantilla`. Las plantillas de D18 (Dueño, Administrador, Cajero, Vendedor, Supervisor de ventas, Bodega, Compras,
   Contador/Auditor) se siembran con sus permisos en `rol_permiso`. Una empresa puede **copiar** una plantilla como rol
   propio y cambiarle permisos.
3. **Migración sin perder acceso:** cada uno de los 12 roles actuales recibe en `rol_permiso` exactamente los permisos que
   hoy le dan sus `@RequiereRol`. Prueba automática: para cada rol y cada endpoint, "antes permitido" = "después permitido".
4. **Permisos sueltos:** tabla `usuario_permiso (usuario_id, permiso_id, concedido TINYINT, empresa_id)` para sumar
   (`concedido=1`) o quitar (`concedido=0`) un permiso a una persona.
5. **Permisos efectivos:** `PermisosService.de(usuario)` = permisos de sus roles + sumados − quitados. Se recuerdan por
   usuario unos segundos (como `AccesoEmpresaService`) y se limpian al cambiar roles o permisos; cambiar permisos sube la
   `version_sesion` para que la sesión se renueve.
6. **Anotación `@RequierePermiso("CODIGO")`** revisada por `RolInterceptor` (suscripción → módulo → permiso). Se reemplazan
   los `@RequiereRol` controlador por controlador; `@RequiereRol` queda deprecada y se borra al final.
7. **Reglas dentro de los servicios:** las 72 referencias a `Roles.X` se revisan una por una. Las que deciden "puede o no
   puede" pasan a `SesionActual.tiene("PERMISO")`; las de segregación (nadie aprueba lo suyo) no cambian. Las políticas de
   aprobación y la delegación siguen apuntando a un rol (ahora puede ser una plantilla o un rol propio).

**Parte B — Pantallas y clave corta**
8. **Usuarios y roles:** en la ficha del trabajador, sus permisos agrupados por módulo con tres estados (del rol, sumado,
   quitado). Pantalla "Roles" para ver plantillas, copiar y editar roles propios.
9. **Clave corta del personal (D10):** `usuario.tipo_credencial` ENUM('CLAVE','PIN'). Personal con PIN: 6 dígitos que asigna
   quien tenga `USUARIO_RESTABLECER_CLAVE`; el trabajador no la cambia. Dueño y administradores (quien tenga permisos de
   administración): clave de 12 a 72 con mayúscula, minúscula, número y símbolo, y MFA. El bloqueo por intentos y el límite
   por IP aplican igual; con PIN se bloquea a los 5 intentos como hoy. **Nunca** se devuelve una clave: se restablece y se
   audita (`USUARIO_RESTABLECER_CLAVE`).

## Pruebas
- **Equivalencia de acceso (la más importante):** prueba que recorre todos los endpoints con `@RequierePermiso` y, para
  cada uno de los 12 roles viejos, compara con la tabla de antes (se genera una vez desde los `@RequiereRol` antes del
  cambio y se guarda como recurso de prueba).
- `PermisosServiceTest`: rol + sumado − quitado; caché que se limpia al cambiar.
- `RolInterceptorTest`: orden suscripción → módulo → permiso; 403 `PERMISO_REQUERIDO`.
- Servicios con reglas por rol: sus pruebas actuales siguen pasando sin cambios de resultado.
- Clave: reglas fuertes para administradores, PIN de 6 dígitos para personal, restablecer no devuelve la clave, auditoría.
- `AislamientoEmpresasTest`: roles propios y permisos sueltos de A no existen para B; una plantilla del sistema no se puede
  editar desde una empresa.
- Frontend: ficha del trabajador con permisos, copiar plantilla, ingreso con PIN.

Verificación final: `./mvnw verify` (con la prueba de equivalencia), `npm test`, `npm run build`, e2e del CI y revisión en
Docker con un usuario de cada rol de la demo (deben ver exactamente lo mismo que antes).

## Riesgos
- Es fácil dejar un endpoint sin permiso o con uno de más: la prueba de equivalencia es obligatoria antes de mezclar.
- Las ramas que estén abiertas cuando se mezcle la Parte A tendrán que cambiar sus `@RequiereRol`: se avisa al equipo y se
  mezcla en un momento sin otras ramas de backend en vuelo.
