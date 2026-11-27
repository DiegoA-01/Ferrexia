# Tasks 035

- [x] T1: Aclarar D12 (2026-11-27: empresas aparte, productos propios, traslados por código con recepción en la destino)
- [x] T2: Reservar y crear `V57__grupo_empresas.sql` (grupo, super administrador, vínculos, traslados, permisos, tipos)
- [x] T3: Grupo en la plataforma: crear, asignar empresas, crear super administrador con usuarios vinculados; pruebas
- [x] T4: Login del grupo con MFA, JWT `grp` y rama en `AuthFilter`; pruebas de que los tokens no se cruzan
- [x] T5: Empresas del grupo, entrar a una empresa (sesión normal y auditoría) y tablero del grupo; pruebas
- [x] T6: Traslados entre empresas: enviar (emparejar por código), recibir con faltantes, anular, kardex y costo; pruebas
- [x] T7: Pruebas de fuga (mismo grupo, grupos distintos, traslados)
- [x] T8: Pantallas: Grupos en la plataforma, ingreso y tablero del grupo, cambiar de empresa, traslados; pruebas
- [ ] T9: `./mvnw verify` (1044 en verde, 1 omitida), `npm test` (372 en verde), tipos regenerados y revisión de
  seguridad (2026-11-27, por el arquitecto: 2 altas, 2 medias y 5 bajas, todas corregidas con pruebas). **Falta:** e2e
  del CI (corre en el PR) y revisión en Docker con dos empresas en un grupo y una fuera.
- [x] T10: Verificar los criterios de aceptación y actualizar el índice de specs
