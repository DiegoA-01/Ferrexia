# Plan 033

Rama `feature/solicitudes-compra-mejoradas`, sacada de `develop` actualizado. Migración propuesta **V53**. Hay que
decidir si la excepción por justificación desaparece del todo o queda solo para el dueño (se anota como decisión nueva
D21 en el README).

## Lo que hay hoy (revisado en el código)
- **Endpoints** (`SolicitudController`): crear, `PUT /{id}` (edita una solicitud propia en BORRADOR o DEVUELTA reemplazando
  sus líneas), enviar, cancelar, decidir, pendientes de aprobación, listar y detalle. Es decir, editar y quitar productos
  **ya existe en el backend** para borrador y devuelta.
- **Alertas:** al enviar, `PRESUPUESTO_EXCEDIDO` y `SOLICITUD_DUPLICADA` (409) se pueden saltar con
  `justificacionExcepcion` (spec 18-a): la solicitud pasa como excepción por la política de mayor tramo.
- **Proveedores:** la solicitud no guarda proveedores. La RFQ (`RfqDatosRequest.proveedorIds`) invita a los elegidos o, si
  viene vacío, a todos los HABILITADOS de las categorías.
- **Frontend:** `features/solicitudes/` tiene el formulario (`solicitud-form`) y la lista; el detalle muestra líneas pero no
  el recorrido completo (RFQ, orden, recepción).

## Backend
1. **Proveedores en la solicitud (V53):** `solicitud_compra.modo_proveedor ENUM('ABIERTA','ESPECIFICA') NOT NULL DEFAULT
   'ABIERTA'` y tabla `solicitud_proveedor (solicitud_id, proveedor_id, empresa_id)`. `SolicitudRequest` suma
   `modoProveedor` y `proveedorIds` (obligatorio si es ESPECIFICA; proveedores HABILITADOS de la empresa).
2. **RFQ:** al crear la RFQ desde una solicitud ESPECIFICA, `proveedorIds` se precarga con los de la solicitud (compras
   puede agregar o quitar, y queda auditado si cambia). ABIERTA funciona como hoy.
3. **Orden directa por contrato** (spec 18-b): si la solicitud es ESPECIFICA, las ofertas de contrato se filtran a esos
   proveedores.
4. **Alertas que bloquean:** en `SolicitudService.enviar`, `PRESUPUESTO_EXCEDIDO` y `SOLICITUD_DUPLICADA` ya no se saltan
   con justificación. Según D21:
   - Opción A (recomendada): solo quien tenga el permiso `SOLICITUD_EXCEPCION` (por defecto la plantilla Dueño) puede
     enviarla con justificación;
   - Opción B: nadie; hay que ajustar el presupuesto o cancelar la duplicada.
   La columna `justificacion_excepcion` se conserva para lo histórico.
5. **Historial:** `GET /api/solicitudes/{id}/recorrido` devuelve, en orden, envío, aprobaciones (con delegación), RFQ
   (número y estado), cotizaciones recibidas, adjudicación, orden(es), recepciones y factura, con fechas y personas. Se arma
   con consultas por `solicitud_id` dentro de la empresa.

## Frontend
- **Formulario:** bloque "¿A quién le pedimos?" con "Abierta a todos los proveedores de la categoría" o "Proveedores
  específicos" (buscador múltiple).
- **Lista:** filtros por estado y fechas; cada fila abre el detalle.
- **Detalle:** productos y cantidades, y la línea de tiempo del recorrido. En BORRADOR o DEVUELTA, botones "Editar",
  "Quitar producto" y "Cambiar cantidad" (usan el `PUT` existente).
- **Alerta:** si sale presupuesto excedido o duplicada, mensaje que explica por qué y qué hacer, sin botón para seguir
  (o con "Enviar como excepción" solo para quien tenga el permiso, según D21).

## Pruebas
- `SolicitudServiceTest`: ESPECIFICA sin proveedores 400; proveedor de otra empresa o no habilitado 409; alerta sin permiso
  bloquea aunque venga justificación; con permiso (opción A) pasa.
- `RfqServiceTest`: RFQ desde solicitud ESPECIFICA precarga invitados.
- Prueba del recorrido con una solicitud que llega hasta factura.
- `AislamientoEmpresasTest`: proveedores elegidos y recorrido de A no se ven en B.
- Frontend: formulario con modo de proveedor, detalle con recorrido, editar en devuelta, alerta que bloquea.

Verificación final: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker con el solicitante, el jefe de
compras y el dueño.
