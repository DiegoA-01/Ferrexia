# Plan 034

Rama `feature/reportes-ventas-ampliados`, sacada de `develop` (`69dbd42`) el 2026-11-27, con la 032 ya mezclada
(tipo de servicio). Migración **V56** (solo el permiso nuevo). Aprobada el 2026-11-27.

## Decisiones (2026-11-27)
- **Margen con permiso:** permiso nuevo `REPORTE_MARGEN` para Dueño (`ADMIN_SISTEMA`), Administrador (`ADMINISTRADOR`)
  y Contador/Auditor (`AUDITOR`). Cajero, Vendedor y Supervisor siguen viendo ventas, unidades, total y devoluciones,
  pero no el costo ni el margen.
- Se reutiliza lo que ya existe: cinco de las seis vistas y la exportación a Excel y PDF.

## Lo que hay hoy (revisado en el código el 2026-11-27)
- `GET /api/reportes/ventas?desde&hasta&agrupar` (`VentaReporteController`, `VentaReporteService` por JDBC y
  `AgregadorVentas`, spec 009) agrupa por `AgrupacionVenta`: DIA, SEMANA, MES, PRODUCTO, CATEGORIA, CLIENTE, VENDEDOR,
  CAJERO, CAJA y MEDIO_PAGO. Permiso `REPORTE_VENTAS` (Dueño, Contador/Auditor, Cajero, Supervisor, Vendedor); quien no
  tiene `VENTA_VER_TODAS` ve solo lo suyo.
- Cada fila (`VentaReporte.Fila`) ya trae ventas, unidades, bruto, devoluciones, neto, base, **costo, margen y margen %**,
  y los totales igual: hoy los ve todo el que entra al reporte.
- Exportar a Excel y PDF ya existe (`ExportacionService.ventas`, botones en `features/ventas/reportes-ventas.html`).
- Falta: la agrupación **por tipo de servicio** (`venta.tipo_servicio`, V53), el **filtro por sede** (la caja tiene
  `sede_id`) y **ocultar costo y margen** sin permiso.

## Backend
1. **V56:** permiso `REPORTE_MARGEN` sembrado para `ADMIN_SISTEMA`, `ADMINISTRADOR` y `AUDITOR`. Sumar la V56 a
   `PermisosDeMigraciones` (pruebas) y al catálogo `agents/specs/022-permisos-plantillas-rol/permisos.md`.
2. `AgrupacionVenta.TIPO_SERVICIO`: agrupa por `venta.tipo_servicio` con nombres "Mostrador" y "Domicilio".
3. Parámetro `sedeId` opcional en `/api/reportes/ventas` y en su exportación: filtra por la sede de la caja. Una sede de
   otra empresa no muestra nada (404 o vacío, nunca datos ajenos).
4. Sin `REPORTE_MARGEN`, la respuesta y la exportación salen con costo, margen, margen % y base sin costo en `null` (en
   filas y totales).
5. Prueba de consistencia: la suma de una agrupación cuadra con el total del reporte por día para el mismo rango
   (anuladas no cuentan, devoluciones restan).

## Frontend
- **Reportes › Ventas:** el selector "Ver por" incluye "Tipo de servicio"; filtro de sede (lista de sedes de la empresa);
  las columnas y tarjetas de costo y margen solo con `REPORTE_MARGEN`. Exportar a Excel y PDF manda los mismos filtros.

## Pruebas
- Agrupación por tipo de servicio y filtro por sede.
- Sin `REPORTE_MARGEN` no salen costo ni margen (pantalla y exportación).
- Consistencia de totales entre agrupaciones.
- `AislamientoEmpresasTest`: la sede de A no trae ventas de B y una sede de B pedida desde A no muestra nada.
- Frontend: vista por tipo de servicio, filtro de sede, margen oculto sin permiso, exportar con filtros.

Verificación final: `./mvnw verify`, `npm test`, tipos regenerados y revisión en Docker con el dueño y el cajero.
