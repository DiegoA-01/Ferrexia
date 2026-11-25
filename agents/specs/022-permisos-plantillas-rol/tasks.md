# Tasks 022

**Antes de empezar**
- [x] T1: Tomar D10 y D18 y anotarlas en `agents/specs/README.md`
- [x] T2: Generar la tabla de acceso actual (endpoint × rol) desde los `@RequiereRol` y guardarla como recurso de prueba

**Parte A — Permisos sin cambiar el acceso**
- [x] T3: Escribir `permisos.md` con el catálogo de permisos por módulo y el mapa rol viejo → permisos
- [x] T4: Reservar la migración y crear `V43__permisos_y_plantillas.sql` (catálogo, `rol.empresa_id`, `es_plantilla`,
  `usuario_permiso`, plantillas y permisos de los 12 roles actuales)
- [x] T5: `PermisosService` (efectivos, caché, versión de sesión) con sus pruebas
- [x] T6: `@RequierePermiso` en `RolInterceptor` con sus pruebas
- [x] T7: Reemplazar `@RequiereRol` por `@RequierePermiso` en los 57 controladores, módulo por módulo
- [x] T8: Revisar las 72 reglas por rol de los servicios y pasar las de "puede o no puede" a permisos (hecha en la
  Parte B; ver "Ajustes de la Parte B" en `plan.md`)
- [x] T9: Prueba de equivalencia de acceso en verde; borrar `@RequiereRol`
- [x] T10: Pruebas de fuga de roles propios y permisos sueltos; `./mvnw verify`; PR de la Parte A (fuga de permisos
  sueltos hecha; la de roles propios va con la Parte B, que los crea)

**Parte B — Pantallas y clave corta**
- [x] T11: API de roles propios (copiar plantilla, editar permisos) y de permisos sueltos por usuario; pruebas
- [x] T12: Clave corta (`tipo_credencial`, PIN de 6 dígitos, reglas fuertes para administradores, restablecer auditado);
  pruebas
- [x] T13: Pantalla de Roles y ficha del trabajador con permisos; pruebas
- [x] T14: Ingreso con PIN en la pantalla de ingreso; pruebas
- [ ] T15: `./mvnw verify`, `npm test`, `npm run build`, e2e del CI y revisión en Docker con cada rol de la demo (hechos
  `./mvnw verify` y `npm test`; faltan `npm run build` y el e2e, que corren en el CI, y la revisión en Docker)
- [x] T16: Verificar los criterios de aceptación y actualizar el índice de specs
