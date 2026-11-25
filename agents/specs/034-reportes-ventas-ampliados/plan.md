# Plan 034

Rama `feature/reportes-ventas-ampliados`, sacada de `develop` actualizado **después de la 032** (tipo de servicio). Sin
migración. Es una spec pequeña: casi todo ya existe.

## Lo que hay hoy (revisado en el código)
- `GET /api/reportes/ventas?desde&hasta&agrupar` (`VentaReporteService` + `AgregadorVentas`, spec 009) ya agrupa por
  `AgrupacionVenta`: DIA, SEMANA, MES, **PRODUCTO, CATEGORIA**, CLIENTE, **VENDEDOR**, CAJERO, **CAJA** y **MEDIO_PAGO**.
  Es decir, cinco de las seis vistas que pidió la reunión ya existen en el backend.
- Falta: **tipo de servicio** (mostrador o domicilio, llega con la 032) y el filtro por **sede** (hoy el reporte no lo tiene).
- Roles: supervisor, admin, auditor, cajero y vendedor (el cajero y el vendedor ven solo lo suyo según el servicio).
- Frontend: `features/ventas/reportes-ventas.html` ya muestra el reporte con su selector de agrupación; la exportación a
  Excel y PDF existe para otros reportes (spec 017 corrigió el formato de miles del PDF de ventas).
- El margen (spec 008) ya se calcula por línea.

## Backend
1. `AgrupacionVenta.TIPO_SERVICIO`: agrupa por `venta.tipo_servicio`.
2. Parámetro `sedeId` opcional en `/api/reportes/ventas` (filtra por la sede de la caja).
3. Cada fila trae: cantidad de ventas, unidades, total, devoluciones y, **solo** con el permiso `REPORTE_VER_MARGEN` (spec
   022; mientras tanto ADMIN_SISTEMA y AUDITOR), costo y margen. Si ya vienen, se revisa que se oculten sin permiso.
4. Exportación: `GET /api/reportes/ventas/exportar?formato=xlsx|pdf` con los mismos parámetros, reutilizando
   `ExportacionService` (mismos permisos, tope de 10.000 filas, auditoría).
5. Prueba de consistencia: la suma de cualquier agrupación = el total del reporte de caja y del cuadre diario (spec 009)
   para el mismo rango (anuladas no cuentan, devoluciones restan).

## Frontend
- **Reportes › Ventas:** selector "Ver por" con las seis vistas de la reunión en lenguaje simple (Categoría, Empleado,
  Producto, Medio de pago, Tipo de servicio, Caja) más las de tiempo que ya hay; filtros de fechas rápidas y sede; columnas
  de margen solo con permiso; botones Excel y PDF.
- Gráfico de barras simple para la vista elegida (el tablero ya usa gráficos; se reutiliza el mismo componente).

## Pruebas
- `AgregadorVentasTest`: agrupación por tipo de servicio; filtro por sede.
- `VentaReporteServiceTest`: sin permiso no salen costo ni margen; consistencia con el cuadre para un rango con anuladas y
  devoluciones.
- Exportación: el Excel tiene las mismas filas y totales que la pantalla.
- `AislamientoEmpresasTest`: el reporte por sede de A no incluye ventas de B.
- Frontend: selector de vistas, filtros, margen oculto sin permiso, exportar.

Verificación final: `./mvnw verify`, `npm test`, `npm run build` y revisión en Docker con el dueño y el auditor.
