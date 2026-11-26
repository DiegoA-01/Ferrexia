# 031 - Proveedor sin perfil: cotizar con un enlace

**Estado:** Hecha (2026-11-26; falta revisión en Docker) · **Rama:** `feature/proveedor-sin-perfil` · **Depende de:** pasos 9 y 12 del backend ·
**Responsable:** Persona 2 · **Puntos de la reunión 2:** 36, 39 · **Migración:** V54 · **Decisión:** D14 (tomada)

## Objetivo
Que el proveedor no tenga que crear una cuenta ni entrar al sistema. La empresa le envía una solicitud y el proveedor
responde desde un enlace, con un formulario corto y protegido contra bots.

## Decisiones (2026-11-26)
- **D14, tal como está:** el proveedor deja de tener cuenta y el portal se retira. Lo que ya existe se conserva como
  historial (cotizaciones, órdenes, facturas); los usuarios con rol PROVEEDOR quedan desactivados.
- **Anti-bot con Cloudflare Turnstile por HTTP**, sin dependencias nuevas (una llamada con el `RestClient` de Spring).
- **No espera a la 033:** los enlaces salen de los proveedores invitados a la RFQ, como hoy.
- Como el proveedor ya no acepta ni despacha la orden en el portal, **compras registra en su nombre** cada paso
  (aceptó, rechazó, está preparando, despachó), con el mismo historial.

## Historias
- Como comprador, quiero enviar una solicitud de cotización a un proveedor por correo o WhatsApp con un enlace.
- Como proveedor, quiero cotizar desde el enlace sin crear una cuenta, escribiendo solo lo necesario.
- Como empresa, no quiero recibir cotizaciones falsas de bots.
- Como comprador, quiero registrar que el proveedor aceptó o despachó la orden y mandarle la orden en PDF.

## Comportamiento
- **Proveedor sin cuenta (D14):** el proveedor es un registro de la empresa (nombre obligatorio; NIT, contacto, correo,
  teléfono y categorías opcionales), sin usuario ni contraseña. El portal del proveedor y su registro público responden
  410 `PORTAL_RETIRADO` (se pueden volver a prender con `PORTAL_HABILITADO=true`).
- **Enlace de un solo uso:** al abrir la RFQ se genera un enlace por proveedor invitado, que vence con la fecha límite de
  la RFQ. Se envía por correo (si el proveedor tiene) y en la RFQ se puede copiar, mandar por WhatsApp o regenerar (el
  anterior deja de servir). Abre un formulario con los productos pedidos donde el proveedor pone precio, impuesto,
  plazo y, si quiere, adjunta su archivo. Una vez enviada la cotización, el enlace queda cerrado.
- **Protección:** límite de peticiones por IP y por enlace, campo trampa y verificación Turnstile al enviar. Un enlace
  vencido, usado o inventado responde igual (404), sin decir cuál de los tres.
- La lectura con IA, el sobre cerrado y el análisis siguen igual.
- **Órdenes y pagos:** compras registra los pasos del proveedor en la orden y le envía la orden en PDF por correo; al
  registrar un pago, el proveedor recibe un aviso por correo. Ya no se ven en un portal.

## Criterios de aceptación
- [x] Se crea un proveedor solo con el nombre (alta de la 026, ahora también en la pantalla Proveedores).
- [x] El proveedor cotiza desde el enlace sin cuenta y el enlace se cierra al enviar o vencer.
- [x] Un enlace vencido, usado o inventado se rechaza (el mismo 404, sin decir cuál).
- [x] Hay límite de peticiones (por IP y por enlace), campo trampa y verificación anti-bot en el formulario.
- [x] Prueba de fuga entre empresas: un enlace de una empresa nunca muestra datos de otra
  (`AislamientoEmpresasTest.elEnlaceDeCotizacionDeUnaEmpresaNuncaMuestraNiGuardaDatosDeOtra`).
- [x] El portal y el registro público responden 410 y compras puede mover la orden en nombre del proveedor.

## Fuera de alcance
- Que el proveedor vea su historial de órdenes y pagos.
- Facturas del proveedor por enlace (compras las registra como hoy).
- Que el proveedor suba su catálogo de precios: sin portal, la carga del catálogo queda para una spec aparte.
