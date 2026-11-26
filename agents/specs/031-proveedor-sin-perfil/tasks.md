# Tasks 031

- [x] T1: Tomar D14 (2026-11-26: tal como está, portal retirado) y aprobar Turnstile por HTTP sin dependencia nueva
- [x] T2: Reservar y crear `V54__enlace_cotizacion.sql` (enlaces, usuarios PROVEEDOR desactivados)
- [x] T3: Alta interna de proveedor con solo el nombre (backend de la 026; botón "Nuevo proveedor" en la pantalla)
- [x] T4: Enlaces de cotización (generar al abrir la RFQ, listar, regenerar, correo); pruebas
- [x] T5: Endpoints públicos `/api/publico/cotizar/{token}` con resolución de empresa en `AuthFilter`; pruebas
- [x] T6: Límite de peticiones, campo trampa y Turnstile (`TurnstileVerificadorTest`, `RateLimitFilterTest`); pruebas
- [x] T7: Retiro del portal y del registro público detrás de `app.portal.habilitado` (`PortalRetiradoFilterTest`)
- [x] T8: Pasos del proveedor registrados por compras, orden en PDF y aviso de pago por correo; pruebas
- [x] T9: Pruebas de fuga y de punta a punta (`AislamientoEmpresasTest`, `EnlacesControllerTest`,
  `CotizacionPublicaServiceTest`)
- [x] T10: Pantallas: nuevo proveedor, enlaces en la RFQ, página pública, acciones de la orden; quitar el portal;
  pruebas (`cotizar-enlace.spec.ts`, `enlaces-rfq.spec.ts`, `orden-acciones.spec.ts`); `sembrar_demo.py` crea el
  proveedor sin cuenta
- [ ] T11: `./mvnw verify` (941 en verde, 1 omitida), `npm test` (327 en verde), tipos regenerados y `tsc` sin
  errores. **Falta:** e2e del CI (corre en el PR) y revisión en Docker con claves de prueba de Turnstile.
- [x] T12: Verificar los criterios de aceptación y actualizar el índice de specs
