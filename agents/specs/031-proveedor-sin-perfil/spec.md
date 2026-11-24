# 031 - Proveedor sin perfil: cotizar con un enlace

**Estado:** Propuesta · **Rama:** `feature/proveedor-sin-perfil` · **Depende de:** pasos 9 y 12 del backend, 033 ·
**Responsable:** Persona 2 · **Puntos de la reunión 2:** 36, 39 · **Migración:** sí (se reserva al empezar) ·
**Decisión:** D14

## Objetivo
Que el proveedor no tenga que crear una cuenta ni entrar al sistema. La empresa le envía una solicitud y el proveedor
responde desde un enlace, con un formulario corto y protegido contra bots.

## Historias
- Como comprador, quiero enviar una solicitud de cotización a un proveedor por correo o WhatsApp con un enlace.
- Como proveedor, quiero cotizar desde el enlace sin crear una cuenta, escribiendo solo lo necesario.
- Como empresa, no quiero recibir cotizaciones falsas de bots.

## Comportamiento
- **Proveedor sin cuenta (D14):** el proveedor es un registro de la empresa (nombre obligatorio; NIT, contacto, correo,
  teléfono y categorías opcionales), sin usuario ni contraseña. El portal del proveedor y su registro público dejan de
  usarse; lo que ya existe se conserva como historial.
- **Enlace de un solo uso:** por cada solicitud a cotizar y cada proveedor se genera un enlace con vencimiento. Se envía
  por correo o se copia para WhatsApp. Abre un formulario con los productos pedidos donde el proveedor pone precio,
  impuesto, plazo y, si quiere, adjunta su archivo. Una vez enviada, el enlace queda cerrado.
- **Protección:** límite de peticiones por enlace e IP, campo trampa y verificación anti-bot (Cloudflare Turnstile o
  reCAPTCHA; agregar la dependencia se pregunta antes, según `AGENTS.md`).
- La lectura con IA, el sobre cerrado y el análisis siguen igual.
- Las órdenes de compra y pagos se envían al proveedor como PDF por correo, ya no se ven en un portal.

## Criterios de aceptación
- [ ] Se crea un proveedor solo con el nombre.
- [ ] El proveedor cotiza desde el enlace sin cuenta y el enlace se cierra al enviar o vencer.
- [ ] Un enlace vencido, usado o inventado se rechaza.
- [ ] Hay límite de peticiones y verificación anti-bot en el formulario.
- [ ] Prueba de fuga entre empresas: un enlace de una empresa nunca muestra datos de otra.

## Fuera de alcance
- Que el proveedor vea su historial de órdenes y pagos.
