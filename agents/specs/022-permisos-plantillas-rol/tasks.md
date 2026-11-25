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
- [ ] T8: Revisar las 72 reglas por rol de los servicios y pasar las de "puede o no puede" a permisos (movida a la
  Parte B: sin roles propios deciden igual que hoy; ver "Ajustes" en `plan.md`)
- [x] T9: Prueba de equivalencia de acceso en verde; borrar `@RequiereRol`
- [x] T10: Pruebas de fuga de roles propios y permisos sueltos; `./mvnw verify`; PR de la Parte A (fuga de permisos
  sueltos hecha; la de roles propios va con la Parte B, que los crea)

**Parte B — Pantallas y clave corta**
- [ ] T11: API de roles propios (copiar plantilla, editar permisos) y de permisos sueltos por usuario; pruebas
- [ ] T12: Clave corta (`tipo_credencial`, PIN de 6 dígitos, reglas fuertes para administradores, restablecer auditado);
  pruebas
- [ ] T13: Pantalla de Roles y ficha del trabajador con permisos; pruebas
- [ ] T14: Ingreso con PIN en la pantalla de ingreso; pruebas
- [ ] T15: `./mvnw verify`, `npm test`, `npm run build`, e2e del CI y revisión en Docker con cada rol de la demo
- [ ] T16: Verificar los criterios de aceptación y actualizar el índice de specs
