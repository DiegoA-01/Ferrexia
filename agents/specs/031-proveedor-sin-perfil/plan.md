# Plan 031

Rama `feature/proveedor-sin-perfil`, sacada de `develop` (`ee83248`) el 2026-11-26. Migración **V54**. D14 tomada:
el portal se retira y el anti-bot es Cloudflare Turnstile por HTTP, sin dependencias nuevas. No espera a la 033.

## Lo que hay hoy (revisado en el código)
- **Alta corta del proveedor:** ya existe desde la 026 (`ProveedorNuevoRequest`, solo `razonSocial` obligatorio, queda
  HABILITADO y sin usuario); `proveedor.nit` ya es NULL (V49).
- **Registro público:** `POST /api/portal/registro/{empresa}` crea proveedor PENDIENTE y usuario PROVEEDOR.
- **Portal con sesión** (`PORTAL_PROVEEDOR`): perfil, catálogo, RFQ invitadas, cotización (ver, guardar, archivo,
  enviar), órdenes (aceptar, rechazar, preparar, despachar), facturas, pagos y recepciones.
- La recepción solo acepta órdenes DESPACHADAS o RECIBIDAS_PARCIAL: sin portal, alguien de compras debe registrar los
  pasos del proveedor.
- `RateLimitFilter` limita 20 POST por minuto por IP a login, refresh y registro. `EmailClient` envía correos (hoy al
  log) con adjunto; `OrdenPdfGenerador` arma el PDF de la orden.

## Backend
1. **V54:** tabla `enlace_cotizacion (id, empresa_id, rfq_id, proveedor_id, version, vence_en, usado_en,
   creado_por_usuario_id, creado_en)`, única por `(rfq_id, proveedor_id)`. Desactiva los usuarios con `proveedor_id`
   (`activo = 0`, sube `version_sesion`) y revoca sus refresh tokens. Sin permisos nuevos: los enlaces usan
   `RFQ_GESTIONAR` y los pasos de la orden `ORDEN_GESTIONAR`.
2. **Token del enlace:** `{id}.{firma}`, donde la firma es HMAC-SHA256 de `id:empresa:version` con una clave derivada
   del secreto del JWT (`JwtUtil.firmar`). En la base no queda nada que sirva para armar el enlace; compras lo puede
   volver a copiar porque se recalcula. Regenerar sube la versión y el anterior deja de servir.
3. **`EnlaceCotizacionService`:** al abrir la RFQ (`RfqService.abrir`) crea un enlace por invitado con
   `vence_en` = fecha límite y lo envía por correo si el proveedor tiene; `GET /api/rfqs/{id}/enlaces` y
   `POST /api/rfqs/{id}/enlaces/{proveedorId}/regenerar` (RFQ_GESTIONAR). URL pública con `app.url-publica`
   (`PUBLIC_URL`).
4. **Endpoints públicos** `/api/publico/cotizar/{token}`: el `AuthFilter` valida la firma con una consulta sin empresa
   (JDBC por id), fija la empresa del enlace y deja el enlace en la petición. `GET` (empresa, RFQ, líneas, borrador y la
   clave pública de Turnstile), `PUT` (borrador), `POST /archivo` y `POST /enviar` (cierra el enlace). Vencido (fecha o
   RFQ que ya no está abierta), usado o inventado: el mismo 404 (`NO_ENCONTRADO`, "El enlace no es válido o ya no está
   disponible") para todos. `CotizacionService` recibe
   el proveedor como parámetro (de la sesión en el portal, del enlace aquí); `DocumentoService.subirDesdeEnlace` guarda
   el archivo sin usuario.
5. **Protección:** `RateLimitFilter` cubre `/api/publico/**` (todos los métodos), contando por IP y por enlace. Campo
   trampa `sitioWeb`: si llega con valor se responde como enviado sin guardar nada. `TurnstileVerificador` llama a
   `siteverify` con `TURNSTILE_SECRET`; sin clave se omite con aviso, salvo con `app.turnstile.obligatorio=true`
   (perfil `prod`), donde responde 409 `ANTIBOT_NO_CONFIGURADO`.
6. **Retiro del portal:** `PortalRetiradoFilter` responde 410 `PORTAL_RETIRADO` a `/api/portal/**` cuando
   `app.portal.habilitado=false` (por defecto). Las pruebas del portal lo prenden en su configuración.
7. **Órdenes:** `POST /api/ordenes/{id}/proveedor/{aceptar|rechazar|preparar|despachar}` (ORDEN_GESTIONAR) reusa las
   transiciones de `OrdenProveedorService` con la nota "registrado por compras". `POST /api/ordenes/{id}/enviar-proveedor`
   manda el PDF de la orden al correo del proveedor (409 `PROVEEDOR_SIN_CORREO`). Al registrar un pago se avisa al
   proveedor por correo si tiene.

## Frontend
- **Proveedores:** botón "Nuevo proveedor" con el formulario corto (si aún no está en la pantalla).
- **RFQ:** sección "Enlaces para cotizar" con estado por invitado y "Copiar", "WhatsApp" (`https://wa.me/...`) y
  "Regenerar".
- **Página pública** `/cotizar/:token` (fuera del área con sesión): productos, precio, impuesto y plazo por línea,
  archivo, widget de Turnstile, campo trampa oculto y "Enviar cotización". Mensaje claro si el enlace no sirve.
- **Orden:** botones "Registrar respuesta del proveedor" y "Enviar al proveedor".
- Se quitan del menú y de las rutas el portal del proveedor y el registro público; el inicio de sesión ya no ofrece
  registrarse como proveedor. nginx permite el script y el marco de `challenges.cloudflare.com`.

## Pruebas
- `EnlaceCotizacionServiceTest`: generar al abrir, firma válida, regenerar invalida el anterior; vencido, usado e
  inventado dan el mismo 404.
- `CotizacionPublicaControllerTest`: ver, guardar, adjuntar y enviar; campo trampa; Turnstile que rechaza.
- `TurnstileVerificadorTest`, `RateLimitFilterTest` con `/api/publico/**`, `PortalRetiradoFilterTest`.
- `OrdenControllerTest`: pasos del proveedor por compras y envío del PDF.
- `AislamientoEmpresasTest`: el enlace de una RFQ de A nunca muestra ni guarda datos de B, y compras de B no ve ni
  regenera los enlaces de A; punta a punta de cotizar por enlace.
- Frontend: página pública, enlaces en la RFQ, acciones de la orden, menú sin portal.

Verificación final: `./mvnw verify`, `npm test`, tipos regenerados y revisión en Docker (crear proveedor solo con nombre,
abrir RFQ, cotizar desde el enlace en una ventana privada, analizar, emitir la orden y registrar el despacho).
