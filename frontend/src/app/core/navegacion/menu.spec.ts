import { describe, expect, it } from 'vitest';

import { MENU } from './menu';

/**
 * Permisos de cada opción tal como estaban antes de agrupar el menú por categorías (spec 011).
 * Reordenar el menú no debe dar ni quitar acceso: si una opción cambia de rol o de módulo, esta tabla lo detecta.
 */
const PERMISOS: Record<string, { roles?: string[]; modulo?: string }> = {
  '/app/inicio': { roles: undefined, modulo: undefined },
  '/app/solicitudes': { roles: undefined, modulo: 'COMPRAS' },
  '/app/cotizaciones': { roles: ['COMPRADOR', 'ADMIN_COMPRAS', 'APROBADOR', 'AUDITOR'], modulo: 'RFQ_COTIZACIONES' },
  '/app/adjudicaciones': { roles: ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'SOLICITANTE', 'COMPRADOR', 'APROBADOR', 'ALMACENISTA', 'CAJERO', 'SUPERVISOR_VENTAS', 'VENDEDOR', 'ADMINISTRATIVO', 'AUDITOR'], modulo: 'RFQ_COTIZACIONES' },
  '/app/pedidos': { roles: ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'SOLICITANTE', 'COMPRADOR', 'APROBADOR', 'ALMACENISTA', 'CAJERO', 'SUPERVISOR_VENTAS', 'VENDEDOR', 'ADMINISTRATIVO', 'AUDITOR'], modulo: 'COMPRAS' },
  '/app/ordenes': { roles: ['COMPRADOR', 'ADMIN_COMPRAS', 'ALMACENISTA', 'ADMINISTRATIVO', 'APROBADOR', 'AUDITOR'], modulo: 'ORDENES_PAGOS' },
  '/app/recepcion': { roles: ['COMPRADOR', 'ADMIN_COMPRAS', 'ALMACENISTA', 'ADMINISTRATIVO', 'APROBADOR', 'AUDITOR'], modulo: 'RECEPCION' },
  '/app/pagos': { roles: ['ADMINISTRATIVO', 'ADMIN_COMPRAS', 'AUDITOR'], modulo: 'ORDENES_PAGOS' },
  '/app/inventario': { roles: undefined, modulo: 'INVENTARIO' },
  '/app/inventario/reabastecimiento': { roles: ['ADMIN_COMPRAS', 'COMPRADOR', 'ALMACENISTA', 'SOLICITANTE', 'AUDITOR', 'ADMIN_SISTEMA'], modulo: 'COMPRAS' },
  '/app/inventario/rotacion': { roles: ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO', 'AUDITOR', 'ALMACENISTA'], modulo: 'REPORTES' },
  '/app/despachos': { roles: ['ALMACENISTA', 'ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR'], modulo: 'DESPACHOS' },
  '/app/inventario/movimientos': { roles: undefined, modulo: 'INVENTARIO' },
  '/app/proveedores': { roles: ['ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR'], modulo: 'PROVEEDORES' },
  '/app/catalogos': { roles: ['ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR', 'ADMIN_SISTEMA'], modulo: 'PROVEEDORES' },
  '/app/pos': { roles: ['CAJERO', 'SUPERVISOR_VENTAS', 'VENDEDOR'], modulo: 'VENTAS' },
  '/app/caja/mi-turno': { roles: ['CAJERO', 'SUPERVISOR_VENTAS'], modulo: 'CAJA' },
  '/app/pedidos-venta': { roles: ['VENDEDOR', 'CAJERO', 'SUPERVISOR_VENTAS', 'ADMIN_SISTEMA'], modulo: 'VENTAS' },
  '/app/ventas': { roles: ['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR', 'VENDEDOR', 'CAJERO'], modulo: 'VENTAS' },
  '/app/ventas/reportes': { roles: ['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR', 'VENDEDOR', 'CAJERO'], modulo: 'VENTAS' },
  '/app/clientes': { roles: ['CAJERO', 'VENDEDOR', 'SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR'], modulo: 'VENTAS' },
  '/app/cartera': { roles: ['CAJERO', 'SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR'], modulo: 'CARTERA' },
  '/app/caja': { roles: ['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR'], modulo: 'CAJA' },
  '/app/tablero': { roles: ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'AUDITOR'], modulo: 'REPORTES' },
  '/app/precios': { roles: ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'AUDITOR'], modulo: 'VENTAS' },
  '/app/reportes': { roles: ['AUDITOR', 'ADMIN_COMPRAS', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO'], modulo: 'REPORTES' },
  '/app/informes': { roles: ['ADMIN_SISTEMA', 'ADMIN_COMPRAS'], modulo: 'COMPRAS' },
  '/app/delegaciones': { roles: ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'SOLICITANTE', 'COMPRADOR', 'APROBADOR', 'ALMACENISTA', 'CAJERO', 'SUPERVISOR_VENTAS', 'VENDEDOR', 'ADMINISTRATIVO', 'AUDITOR'], modulo: 'COMPRAS' },
  '/app/auditoria': { roles: ['AUDITOR', 'ADMIN_SISTEMA'], modulo: 'AUDITORIA' },
  '/app/usuarios': { roles: ['ADMIN_SISTEMA', 'AUDITOR'], modulo: undefined },
  '/app/configuracion': { roles: ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'AUDITOR', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO'], modulo: undefined },
  '/app/configuracion/notificaciones': { roles: ['ADMIN_SISTEMA'], modulo: undefined },
};

const opciones = () => MENU.flatMap(s => s.opciones);
const seccionDe = (ruta: string) => MENU.find(s => s.opciones.some(o => o.ruta === ruta))?.titulo;

describe('Menú lateral por categorías (spec 011)', () => {
  it('tiene los siete grupos en orden, con Inicio sola y sin título', () => {
    expect(MENU.map(s => s.titulo)).toEqual(['', 'Ventas', 'Compras', 'Inventario', 'Reportes', 'Gestión humana', 'Gestión']);
    expect(MENU[0].opciones.map(o => o.ruta)).toEqual(['/app/inicio']);
  });

  it('conserva las 32 opciones sin repetir rutas', () => {
    const rutas = opciones().map(o => o.ruta);
    expect(rutas.length).toBe(32);
    expect(new Set(rutas).size).toBe(32);
    expect([...rutas].sort()).toEqual(Object.keys(PERMISOS).sort());
  });

  it('ninguna opción gana ni pierde acceso', () => {
    for (const o of opciones()) {
      expect({ ruta: o.ruta, roles: o.roles, modulo: o.modulo })
        .toEqual({ ruta: o.ruta, roles: PERMISOS[o.ruta].roles, modulo: PERMISOS[o.ruta].modulo });
    }
  });

  it('cada pantalla queda en el grupo acordado', () => {
    expect(seccionDe('/app/precios')).toBe('Reportes');
    expect(seccionDe('/app/tablero')).toBe('Reportes');
    expect(seccionDe('/app/ventas/reportes')).toBe('Reportes');
    expect(seccionDe('/app/inventario/rotacion')).toBe('Reportes');
    expect(seccionDe('/app/informes')).toBe('Reportes');
    expect(seccionDe('/app/proveedores')).toBe('Compras');
    expect(seccionDe('/app/catalogos')).toBe('Compras');
    expect(seccionDe('/app/usuarios')).toBe('Gestión humana');
    expect(seccionDe('/app/delegaciones')).toBe('Gestión humana');
    expect(seccionDe('/app/auditoria')).toBe('Gestión');
  });

  it('"Reportes" pasa a llamarse "Reportes de compras" sin cambiar de ruta', () => {
    expect(opciones().find(o => o.ruta === '/app/reportes')?.texto).toBe('Reportes de compras');
  });

  it('los contadores siguen en sus opciones', () => {
    expect(opciones().find(o => o.ruta === '/app/solicitudes')?.contador).toBe('solicitudes');
    expect(opciones().find(o => o.ruta === '/app/inventario')?.contador).toBe('bajoMinimo');
  });
});
