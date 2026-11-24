# Tasks 015

- [x] T1: `CajaResponse` con `turnoAbiertoPor` y `turnoAbiertoDesde`; repositorio y servicio; pruebas de servicio y
  controlador (sin montos)
- [x] T2: Prueba de fuga en `AislamientoEmpresasTest`
- [x] T3: Tipo `Caja` del frontend con los campos nuevos
- [x] T4: "Mi turno": cajas ocupadas deshabilitadas con quién y desde cuándo, una sola libre elegida sola, aviso si
  todas están ocupadas; pruebas en `mi-turno.spec.ts`
- [x] T5: `./mvnw test`, `npm test` y `npm run build` sin advertencias
- [x] T6: Reconstruir `api` y `web` en Docker y revisar con el supervisor de ventas
- [x] T7: Verificar los criterios de aceptación y actualizar el índice de specs

Notas (2026-11-23):
- No existían `CajaServiceTest` ni `CajaControllerTest`: se creó `CajaServiceTest` y el JSON real se revisa en la
  prueba de cajas de `AislamientoEmpresasTest` (la de controlador del plan quedó cubierta ahí).
- La prueba del POS "sin turno abierto lleva a Mi turno" pasa de 5 s cuando el backend corre a la vez; sola y en la
  corrida sin carga pasa. Ya fallaba así antes de esta spec.
