# Tasks 011

- [x] T1: Escribir `menu.spec.ts` con la tabla fija de permisos del menú actual (debe pasar antes del cambio en roles y
  módulos, y fallar en el orden de las secciones)
- [x] T2: Reordenar `MENU` en las siete secciones y renombrar "Reportes" a "Reportes de compras"
- [x] T3: En `shell.html`, no pintar el título de una sección sin título; sumar las pruebas de `shell.spec.ts`
- [x] T4: `npm test` y `npm run build` sin advertencias
- [x] T5: Reconstruir `web` en Docker y revisar el menú con el jefe de compras, el cajero y el auditor (el dueño
  entra con MFA: lo cubre `shell.spec.ts`)
- [x] T6: Verificar los criterios de aceptación de la spec y actualizar el índice de specs

Notas (2026-11-23): al revisar los permisos se corrigió la spec antes de programar: "Delegaciones" la ve todo usuario
interno y "Reportes de ventas" la ve el cajero, así que el cajero sí ve "Gestión humana" y "Reportes"; el ejemplo pasó a
"el cajero no ve Gestión". Revisión en Docker con jefe de compras, cajero y auditor.
