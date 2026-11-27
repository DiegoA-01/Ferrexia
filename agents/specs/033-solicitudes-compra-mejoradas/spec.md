# 033 - Solicitudes de compra: proveedor elegido, historial y alertas que bloquean

**Estado:** Hecha (2026-11-26) · **Rama:** `fix/ajustes-prueba-usuarios` · **Depende de:** pasos 10, 11 y 18-a ·
**Responsable:** Persona 2 · **Puntos de la reunión 2:** 37, 40, 42

## Objetivo
Que la solicitud de compra se pueda dirigir a un proveedor concreto o abrirse a todos, que se vea su historial completo
y se pueda corregir, y que una alerta no se pueda pasar por alto.

## Historias
- Como solicitante, quiero pedir a un proveedor específico o dejar que se invite a todos los de esa categoría.
- Como solicitante, quiero ver el historial de mis solicitudes y el detalle de lo que pedí.
- Como solicitante, quiero quitar o cambiar un producto de mi solicitud si ya no lo necesito.
- Como administrador de compras, no quiero que se cree una solicitud cuando sale una alerta.

## Comportamiento
- **Proveedor de la solicitud:** al crear la solicitud se elige "Proveedor específico" (uno o varios) o "Abierta a
  todos" (los habilitados de la categoría, como hoy). Pasa así a la RFQ.
- **Historial y detalle:** la lista de solicitudes muestra todas con su estado y, al abrir una, el detalle de productos,
  cantidades, aprobaciones y lo que pasó después (RFQ, orden, recepción).
- **Editar o quitar productos:** mientras la solicitud está en borrador o devuelta, se pueden cambiar o quitar productos.
  Después de enviada no se edita (se cancela y se crea otra), para no romper la aprobación.
- **Alertas que bloquean:** si sale la alerta de solicitud duplicada o de presupuesto excedido, la solicitud **no se
  puede crear** (se quita la excepción con justificación de la spec 18-a), salvo que el equipo decida dejar la
  excepción solo al dueño.

## Criterios de aceptación
- [x] La solicitud se dirige a proveedores específicos o queda abierta a todos.
- [x] El historial muestra cada solicitud con su detalle y recorrido.
- [x] En borrador o devuelta se pueden cambiar o quitar productos.
- [x] Con alerta de duplicada o presupuesto, la solicitud no se crea.
- [x] Prueba de fuga entre empresas para los proveedores elegidos.

## Fuera de alcance
- Proveedores sin cuenta (spec 031).
