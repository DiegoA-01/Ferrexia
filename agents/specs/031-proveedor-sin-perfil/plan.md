# Plan 031

Rama `feature/proveedor-sin-perfil`, sacada de `develop` actualizado **después de la 033** (proveedores elegidos en la
solicitud). Migración propuesta **V51**. Decisión **D14** debe estar tomada. Agregar la verificación anti-bot se pregunta
antes (regla de `AGENTS.md`), aunque se propone hacerla con una llamada HTTP sin librerías nuevas.

## Lo que hay hoy (revisado en el código)
- **Registro público:** `POST /api/portal/registro/{empresa}` (y `/categorias`) crea el proveedor PENDIENTE y un usuario con
  rol PROVEEDOR; compras lo habilita, rechaza o suspende (`/api/proveedores/{id}/habilitar|rechazar|suspender`).
- **Portal con sesión:** `/api/portal/proveedor` (perfil, evaluaciones, categorías), `/api/portal/rfqs` (RFQ a las que fue
  invitado) y `/api/portal/rfqs/{rfqId}/cotizacion` (ver, guardar, subir archivo, enviar). El proveedor también ve sus
  órdenes y pagos.
- `proveedor.nit` es NOT NULL y único por empresa. No hay alta interna de proveedor (salvo la mínima si la 026 llegó
  antes).
- `RateLimitFilter` limita 20 POST por minuto por IP a login, refresh y registro.

## Backend
1. **Proveedor sin cuenta:** `POST /api/proveedores` (ADMIN_COMPRAS, COMPRADOR) con `razonSocial` obligatorio y NIT,
   contacto, correo, teléfono y categorías opcionales. Queda HABILITADO y sin usuario. V51: `proveedor.nit` NULL (si la 026
   no lo hizo) y `proveedor.whatsapp VARCHAR(20) NULL`.
2. **Enlace de cotización (V51):** tabla `enlace_cotizacion (id, empresa_id, rfq_id, proveedor_id, token_hash CHAR(64),
   vence_en, usado_en, creado_por_usuario_id, creado_en)`, único por `token_hash`. El token es aleatorio de 256 bits; en la
   base solo queda su SHA-256 (igual que los refresh tokens).
   - Al abrir la RFQ se genera un enlace por proveedor invitado, con vencimiento = fecha de cierre de la RFQ.
   - Se envía por correo (si hay) y queda disponible para copiar y mandar por WhatsApp desde la RFQ
     (`GET /api/rfqs/{id}/enlaces`, solo compras).
   - `POST /api/rfqs/{id}/enlaces/{proveedorId}/regenerar` invalida el anterior.
3. **Endpoints públicos** bajo `/api/publico/cotizar/{token}` (sin sesión; el `AuthFilter` resuelve la empresa desde el
   enlace, como hoy el registro por código):
   - `GET`: datos de la RFQ (productos, cantidades, fecha límite) y nombre de la empresa; nada más.
   - `PUT`: guardar borrador de la cotización (precio, impuesto, plazo por línea).
   - `POST /archivo`: adjuntar PDF o imagen (mismas reglas de `DocumentoService`: 10 MB, firma real).
   - `POST /enviar`: envía; el enlace queda `usado_en` y cerrado.
   - Enlace vencido, usado o inventado: 404 genérico (no se revela cuál de los tres).
4. **Protección:**
   - `RateLimitFilter` incluye `/api/publico/**` (por IP y por token).
   - Campo trampa en el formulario (si llega con valor, se descarta en silencio).
   - Verificación anti-bot con Cloudflare Turnstile por HTTP (`RestClient`, sin dependencia nueva) en `POST /enviar`; la
     clave secreta en `TURNSTILE_SECRET` (si falta en desarrollo, se omite con un aviso en el log; en `prod` es
     obligatoria).
5. **Cotización:** el resto no cambia (sobre cerrado, lectura con IA, análisis). `Cotizacion` guarda `origen = ENLACE`.
6. **Portal viejo:** según D14, los endpoints del portal y del registro público se dejan de mostrar en la interfaz y
   responden 410 `PORTAL_RETIRADO` detrás de una propiedad `app.portal.habilitado=false`; los usuarios PROVEEDOR existentes
   se desactivan con una tarea de la migración (sin borrar datos).
7. **Órdenes y pagos al proveedor:** se envían como PDF por correo (la exportación de la orden a PDF ya existe).

## Frontend
- **Proveedores:** botón "Nuevo proveedor" con formulario corto (solo nombre obligatorio).
- **RFQ:** sección "Enlaces para cotizar" con un enlace por invitado, botones "Copiar", "Enviar por WhatsApp" (abre
  `https://wa.me/...` con el texto) y "Regenerar".
- **Página pública** `/cotizar/:token` (fuera del área con sesión): productos de la RFQ, campos por línea, archivo, widget
  de Turnstile y "Enviar cotización". Mensaje claro si el enlace no sirve.
- Se quitan del menú y de las rutas el portal del proveedor y el registro público (según D14).

## Pruebas
- `EnlaceCotizacionServiceTest`: generar guarda solo el hash; regenerar invalida el anterior; vencido, usado e inventado dan
  404 igual.
- Endpoints públicos: guardar, adjuntar y enviar; segundo envío 404; campo trampa descarta; Turnstile falso rechaza (con un
  `RestClient` simulado).
- `RateLimitFilterTest` con `/api/publico/**`.
- `AislamientoEmpresasTest`: el token de una RFQ de A nunca muestra ni guarda datos de B; compras de B no ve los enlaces de
  A.
- Frontend: página pública (sin sesión), enlaces en la RFQ, nuevo proveedor.
- E2E: cotizar por enlace de punta a punta (con Turnstile en modo de prueba).

Verificación final: `./mvnw verify`, `npm test`, `npm run build`, e2e y revisión en Docker (crear proveedor solo con
nombre, abrir RFQ, cotizar desde el enlace en una ventana privada, analizar).
