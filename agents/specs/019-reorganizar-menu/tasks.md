# Tasks 019

- [x] T1: Comparar con el equipo qué se vio "dentro de Ventas" (rutas `/app/ventas/reportes` y Precios y margen) y anotar
  la conclusión en la spec — anotada en la spec; se confirma con el equipo al revisar el PR
- [x] T2: Ruta `/app/reportes/ventas` con redirección desde la vieja; enlaces del tablero y migas; pruebas
- [x] T3: Mover "Precios y margen" a Ventas sin cambiar roles; prueba de rutas por rol antes y después
- [x] T4: Pantalla "Importación de datos" en Configuración con las cargas de productos y precios (diálogos reutilizados);
  pruebas
- [x] T5: En Inventario y Precios, cambiar el botón de carga por el enlace al apartado nuevo; ajustar pruebas
- [x] T6: Revisar Inicio y pestañas con el menú nuevo; sumar la pantalla al e2e de "sin scroll"
- [ ] T7: `npm test`, `npm run build` sin advertencias y revisión en Docker — `npm test` en verde (278), `tsc` sin
  errores y e2e completo en Docker en verde (5 de 5, incluido el jefe de compras en Importación de datos); falta el
  build de producción (Google Fonts sin red en este equipo; lo hace el CI)
- [x] T8: Verificar los criterios de aceptación y actualizar el índice de specs — revisada en el navegador con el jefe
  de compras y aprobada el 2026-11-25

Notas (2026-11-25):
- Los reportes, el informe de compras y el tablero ya estaban en la categoría Reportes desde la spec 011. El cambio real
  fue la ruta (`/app/reportes/ventas`) y sacar "Precios y margen" de Reportes.
- "Reportes de compras" (`/app/reportes`) quedó con `exacta: true` para que no se marque al abrir los reportes de ventas,
  que ahora cuelgan de su ruta.
- Las cargas se pasaron a componentes propios (`features/configuracion/importacion/carga-productos.ts` y
  `carga-precios.ts`) con la misma lógica; Inventario y Precios ahora tienen un enlace "Importar desde Excel".
- La carga de precios la ve el dueño y, si el dueño se los delegó (`puedeEditar` del backend), el jefe de compras: es la
  misma regla que tenía la pantalla de Precios.
- La tarjeta de Importación en el índice de Configuración solo la ven el dueño y el jefe de compras (las otras tarjetas
  tienen modo consulta; esta no).
- En la misma rama: los nombres de Docker pasan a `ferrexia` y se actualizó `PROGRESS.md` (sección 8).
