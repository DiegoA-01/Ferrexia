# Tasks 025

- [x] T1: Tomar D15 y anotarla — tomada el 2026-11-25 tal como está
- [x] T2: V47: el Dueño (y la plantilla Administrador) abre turno y cobra (`TURNO_OPERAR`, `VENTA_COBRAR`); permisos nuevos
  `CAJA_RETIRO_SIN_PIN`, `CAJA_GASTO_SIN_PIN` y `CAJA_INGRESO_SIN_PIN` para Dueño, Administrador y Supervisor de ventas;
  tabla `caja_usuario`
- [x] T3: Cajas por persona: `GET /api/cajas/mias`, `GET/PUT /api/cajas/{id}/usuarios`; abrir turno en una caja no
  asignada da 403 `CAJA_NO_ASIGNADA`; sin asignaciones, todas; pruebas (`TurnoServiceTest`)
- [x] T4: Retiro, gasto e ingreso sin PIN con el permiso de su tipo; sin él, el PIN como siempre; la auditoría anota
  `sinPin=true`; pruebas (`TurnoServiceTest`)
- [x] T5: Prueba de fuga (`AislamientoEmpresasTest`): asignar una persona de otra empresa da 409, B no ve ni cambia la
  asignación de A, el dueño ve sus cajas en `/mias`; la foto de acceso de la 022 acepta como cambio decidido que el Dueño
  opere el turno y cobre
- [x] T6: Frontend: "Mi turno" pide solo las cajas de la persona; el retiro, gasto o ingreso con permiso dice "Registrar"
  sin PIN; el POS y Pedidos de venta deciden "cobrar" por el permiso `VENTA_COBRAR`; en Cajas y turnos › editar caja,
  "Quién puede usarla"; pruebas (`turno-formularios.spec.ts`, `mi-turno.spec.ts`)
- [ ] T7: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker — hechos: `./mvnw verify` en verde (con
  MySQL real y Javadoc), `npm test` (309) y `tsc` sin errores; en Docker V47 y V48 aplicadas, e2e 5 de 5 y revisado con
  el dueño: "Punto de venta" y "Mi turno" en su menú, abrir turno en la Caja 1 y, al editar la caja, "Quién puede
  usarla" con las 12 personas internas (sin el usuario del proveedor). Falta el build (CI) y la aprobación
- [ ] T8: Verificar los criterios de aceptación y actualizar el índice de specs

Notas (2026-11-25):
- Migraciones V47 (la V46 la tomó la 024) y V48: en la revisión en Docker se vio que el dueño no tenía el permiso de
  cotizar (`VENTA_COTIZAR`) que pide el Punto de venta; la V48 se lo da a Dueño y Administrador. Se trabaja en `feature/cierre-por-denominacion` con la 023, 024 y 027.
- El permiso sin PIN es uno por tipo de movimiento, para que el dueño pueda dar, por ejemplo, retiros sin PIN pero no
  gastos. Por defecto lo tienen Dueño, Administrador y Supervisor de ventas; a un cajero de confianza se le da como
  permiso suelto en su ficha (spec 022).
- La ficha del trabajador (Editar usuario) muestra sus cajas en solo lectura, con `GET /api/cajas/de-usuario/{id}`
  (`usuarios-cajas.spec.ts` y prueba de fuga); se asignan desde la caja.
- Limitación conocida: nadie aprueba la diferencia de su propio turno, así que si el dueño es el único que aprueba, la
  diferencia de su turno queda pendiente.
