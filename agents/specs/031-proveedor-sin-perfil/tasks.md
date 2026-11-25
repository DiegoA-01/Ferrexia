# Tasks 031

- [ ] T1: Tomar D14 (retiro del portal) y aprobar Turnstile por HTTP sin dependencia nueva
- [ ] T2: Reservar la migración y crear `V51__enlace_cotizacion.sql` (enlaces, NIT opcional, WhatsApp, usuarios PROVEEDOR
  desactivados)
- [ ] T3: Alta interna de proveedor con solo el nombre; pruebas
- [ ] T4: Enlaces de cotización (generar al abrir la RFQ, listar, regenerar, correo); pruebas
- [ ] T5: Endpoints públicos `/api/publico/cotizar/{token}` con resolución de empresa en `AuthFilter`; pruebas
- [ ] T6: Límite de peticiones, campo trampa y Turnstile; pruebas
- [ ] T7: Retiro del portal y del registro público detrás de `app.portal.habilitado`; pruebas
- [ ] T8: Pruebas de fuga
- [ ] T9: Pantallas: nuevo proveedor, enlaces en la RFQ y página pública de cotización; quitar el portal del menú; pruebas
- [ ] T10: E2E de cotizar por enlace
- [ ] T11: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker
- [ ] T12: Verificar los criterios de aceptación y actualizar el índice de specs
