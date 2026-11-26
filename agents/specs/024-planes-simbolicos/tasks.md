# Tasks 024

- [x] T1: Tomar D11 y anotarla — tomada el 2026-11-25: sin planes; los módulos se marcan al crear la empresa y luego se
  prenden o apagan uno por uno; las empresas existentes quedan con todos
- [x] T2: `AccesoEmpresaService`: todos los módulos del catálogo menos los apagados (el plan no cuenta); suspensión y mora
  igual; V46 borra las excepciones de antes; pruebas (`AccesoEmpresaServiceTest`)
- [x] T3: Consola: crear y editar planes responden 409 `PLANES_RETIRADOS`; el alta recibe los módulos marcados (sin plan
  obligatorio) y apaga los demás; prender o apagar un módulo; pruebas (`EmpresaPlataformaServiceTest`,
  `PlataformaConsolaControllerTest`)
- [x] T4: Prueba con MySQL (`AislamientoEmpresasTest`): el alta con Ventas y sin Reportes, el plan PROFESIONAL ya no
  devuelve un módulo apagado, y crear un plan da 409
- [x] T5: Consola en pantalla: sin plan en el alta, la lista ni el detalle; panel de módulos con "Marcar todos"; la
  pantalla "Planes y módulos" sale del menú y su dirección lleva a Empresas; pruebas (`empresas.spec.ts`)
- [x] T6: Empresa: los candados que quedan son solo los de módulos apagados por la plataforma (sin cambios de código)
- [ ] T7: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker — hechos: `./mvnw verify` en verde (con
  MySQL real y Javadoc), `npm test` (306) y `tsc` sin errores; en Docker V46 aplicada, e2e 5 de 5 y revisada la consola
  con el dueño de la plataforma: el alta sin plan y con el panel de 13 módulos ("0 de 13 marcados", "Marcar todos"), el
  menú sin "Planes y módulos", la lista sin columna de plan y la empresa demo con sus 13 módulos habilitados. Falta el
  build (CI) y la aprobación
- [ ] T8: Verificar los criterios de aceptación y actualizar el índice de specs

Notas (2026-11-25):
- La decisión cambió el plan original (planes como etiqueta): ahora no hay planes en pantalla. `suscripcion.plan_id` sigue
  en la base (no admite nulo) y el alta guarda el plan EMPRESARIAL si no se manda ninguno; no decide nada.
- En `empresa_modulo` solo quedan los módulos apagados (`habilitado = false`). Prender un módulo borra su fila.
- El alta sin la lista de módulos (llamadas viejas) habilita todos; con la lista vacía responde 409 `SIN_MODULOS` y con
  un módulo que no existe, 409 `MODULO_INEXISTENTE`.
- Se trabaja en `feature/cierre-por-denominacion` junto con la 023 y la 027, por pedido del equipo.
