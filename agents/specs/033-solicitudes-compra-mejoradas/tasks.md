# Tasks 033

Se trabaja en `fix/ajustes-prueba-usuarios` por pedido del usuario (2026-11-26). Migración **V55** (la V53 del plan ya
la tomó la spec 032).

- [x] T1: D21 tomada con la opción recomendada: solo quien tenga `SOLICITUD_EXCEPCION` (plantilla Dueño) envía con
      justificación; anotada en el README
- [x] T2: Reservar la migración y crear `V55__solicitud_proveedor.sql` (modo, `solicitud_proveedor`, permiso)
- [x] T3: Modo de proveedor y proveedores en la solicitud (habilitados y de la empresa); `GET /api/solicitudes/proveedores`;
      pruebas
- [x] T4: RFQ con los proveedores de la solicitud (compras puede cambiarlos y queda en la auditoría); ofertas de contrato y
      orden directa solo con esos proveedores; pruebas
- [x] T5: Alertas que bloquean según D21; pruebas
- [x] T6: `GET /api/solicitudes/{id}/recorrido` (envío, aprobaciones, RFQ, cotizaciones, adjudicación, órdenes,
      recepciones y facturas); prueba del endpoint
- [x] T7: Pruebas de fuga (`AislamientoEmpresasTest`): proveedores elegidos y recorrido de A no se ven en B
- [x] T8: Formulario con "¿A quién le pedimos?"; la RFQ nueva llega con esos proveedores marcados; pruebas
- [x] T9: Detalle con a quién se pidió y el recorrido; "Cambiar o quitar productos y enviar" en borrador o devuelta (usa el
      `PUT` que ya existía); alerta que bloquea sin permiso; pruebas
- [x] T10: `./mvnw verify` (950 en verde), `npm test` (337 en verde) e imagen Docker con V55; revisado por el usuario
- [x] T11: Criterios verificados y aprobados por el usuario en Docker (2026-11-26); índice actualizado

Fuera de esta entrega: filtro por fechas en la lista de solicitudes (el backend no lo tiene; hoy filtra por estado y
consecutivo).
