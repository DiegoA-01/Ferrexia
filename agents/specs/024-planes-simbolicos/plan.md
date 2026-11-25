# Plan 024

Rama `feature/planes-simbolicos`, sacada de `develop` actualizado. Sin migración de esquema (las tablas de planes se
conservan). Decisión **D11** debe estar tomada (en especial: si se conservan las excepciones de módulo por empresa).

## Lo que hay hoy (revisado en el código)
- 13 módulos en `Modulos.java`: INVENTARIO, PROVEEDORES, COMPRAS, RFQ_COTIZACIONES, IA, ORDENES_PAGOS, RECEPCION, DESPACHOS,
  REPORTES, AUDITORIA, VENTAS, CAJA y CARTERA.
- `AccesoEmpresaService` arma los módulos de una empresa = módulos de su plan (`plan_modulo`) ± sus excepciones
  (`empresa_modulo`), y el estado de la suscripción. Lo recuerda 30 s por empresa. `RolInterceptor` responde 403
  `MODULO_NO_CONTRATADO` si el módulo no está.
- Consola (`PlataformaPlanController`): `GET /modulos`, `GET /planes`, `POST /planes`, `PUT /planes/{id}`.
  `PlataformaEmpresaController`: cambiar plan (`PUT /{id}/plan`), excepción de módulo (`PUT /{id}/modulos/{modulo}`) y
  estado (`PATCH /{id}/estado`).
- La empresa ve sus módulos en `GET /api/empresa/modulos`; el menú pone candado a los apagados.

## Backend
1. **Todos los módulos:** `AccesoEmpresaService` devuelve siempre los 13 módulos para cualquier empresa con suscripción
   válida. Según D11:
   - Opción A (recomendada): se ignoran también las excepciones (ningún módulo se apaga por empresa).
   - Opción B: se conservan solo las excepciones que **quitan** un módulo (el dueño de la plataforma puede apagar uno a una
     empresa puntual).
2. **Suscripción igual que hoy:** PRUEBA/ACTIVA entran; MOROSA solo lectura; SUSPENDIDA y CANCELADA no entran.
3. **Consola:** `POST /planes` y `PUT /planes/{id}` responden 409 `PLANES_SIMBOLICOS` ("Los planes ya no cambian los
   módulos"). Cambiar el plan de una empresa sigue permitido (es una etiqueta). Si D11 es A, `PUT /{id}/modulos/{modulo}`
   responde igual 409.
4. `@RequiereModulo` se queda en los controladores (no estorba y sirve si algún día se vuelve a cobrar por módulo).
5. `GET /api/empresa/modulos` devuelve los 13 activos; el plan sale como texto.

## Frontend
- **Consola de plataforma:** la pantalla de planes queda de solo lectura (nombre y descripción, sin módulos ni botones de
  crear o editar). El detalle de empresa muestra el plan como etiqueta y quita los interruptores de módulos (según D11).
- **Empresa:** sin candados por plan en menú, Inicio y pestañas (ya salen solos al recibir los 13 módulos). Se quita el
  texto "Módulo no contratado" donde aparezca fijo.

## Pruebas
- `AccesoEmpresaServiceTest`: una empresa con plan BASICO tiene VENTAS, CAJA y CARTERA; la suspendida sigue sin entrar; la
  morosa sigue en solo lectura; excepciones según D11.
- `PlanPlataformaServiceTest` / controlador: crear o editar plan da 409.
- `AislamientoEmpresasTest` (consola): una empresa BASICO usa `/api/ventas` sin 403.
- Frontend: consola sin edición de planes; menú sin candados para una empresa BASICO.

Verificación final: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker con una empresa en plan BASICO y con
el dueño de la plataforma.
