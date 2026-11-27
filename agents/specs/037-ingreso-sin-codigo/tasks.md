# Tasks 037

- [x] T1: Decidir el correo único en todo el sistema y aprobar la spec (2026-11-27)
- [x] T2: Reservar y crear `V58__correo_unico_global.sql` (comprobación de repetidos y unicidad sin los internos de grupo)
- [x] T3: Login y recuperación de clave solo con correo (búsqueda sin empresa); pruebas
- [x] T4: Correo único global al crear o editar usuarios (sin romper los internos del super administrador); pruebas
- [x] T5: Consola de plataforma sin mostrar el código; pruebas
- [x] T6: Pantallas: ingreso y recuperar sin código; plataforma y grupo sin el código; pruebas
- [x] T7: `sembrar_demo.py`, e2e y documentos (`CLAUDE.md`, skill) sin el código
- [x] T8: Prueba de fuga y ajuste de `AislamientoEmpresasTest`
- [ ] T9: `./mvnw verify` (1045 en verde, 1 omitida), `npm test` (371 en verde), tipos regenerados y `tsc` sin
  errores. **Falta:** e2e del CI (corre en el PR) y revisión en Docker (reconstruir y resembrar la demo).
- [x] T10: Verificar los criterios de aceptación y actualizar el índice de specs
