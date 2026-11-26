import { Acceso, conPermiso, soloInternos } from '../sesion/acceso';

/** Opción del menú lateral. Se muestra si el usuario tiene el rol (o su permiso, spec 022); si el módulo está apagado sale bloqueada. */
export interface OpcionMenu {
  texto: string;
  icono: string;
  ruta: string;
  roles?: Acceso;
  modulo?: string;
  /** {@code true} si solo se marca activa en su ruta exacta (porque otras opciones cuelgan de ella). */
  exacta?: boolean;
  /** Clave del contador que se muestra a la derecha. */
  contador?: 'solicitudes' | 'bajoMinimo';
}

export interface SeccionMenu {
  titulo: string;
  /** Icono de la categoría (Material Symbols); la sección de Inicio no lleva. */
  icono?: string;
  opciones: OpcionMenu[];
}

/** Roles que leen órdenes (mismos que {@code GET /api/ordenes}). */
export const ROLES_ORDENES = conPermiso(['COMPRADOR', 'ADMIN_COMPRAS', 'ALMACENISTA', 'ADMINISTRATIVO', 'APROBADOR', 'AUDITOR'], 'ORDEN_VER');
/** Roles que ven la recepción (mismos que {@code GET /api/recepciones}). */
export const ROLES_RECEPCION = conPermiso(['COMPRADOR', 'ADMIN_COMPRAS', 'ALMACENISTA', 'ADMINISTRATIVO', 'APROBADOR', 'AUDITOR'], 'RECEPCION_VER');
/** Roles que ven RFQ y análisis (mismos que {@code GET /api/rfqs}). */
export const ROLES_RFQ = conPermiso(['COMPRADOR', 'ADMIN_COMPRAS', 'APROBADOR', 'AUDITOR'], 'RFQ_VER');
/** Roles que leen pagos (mismos que {@code GET /api/pagos}). */
export const ROLES_PAGOS = conPermiso(['ADMINISTRATIVO', 'ADMIN_COMPRAS', 'AUDITOR'], 'PAGO_BUSCAR');
/** Roles que leen despachos (mismos que {@code GET /api/despachos}). */
export const ROLES_DESPACHOS = conPermiso(['ALMACENISTA', 'ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR'], 'DESPACHO_VER');
/** Roles que ven la rotación de productos (mismos que {@code GET /api/reportes/rotacion}). */
export const ROLES_ROTACION = conPermiso(['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO', 'AUDITOR', 'ALMACENISTA'], 'REPORTE_ROTACION');
/** Roles que leen reportes de gasto y proveedores. */
/** Roles que ven el plan de reabastecimiento (mismos que {@code GET /api/reabastecimiento}). */
export const ROLES_REABASTECIMIENTO = conPermiso(['ADMIN_COMPRAS', 'COMPRADOR', 'ALMACENISTA', 'SOLICITANTE', 'AUDITOR', 'ADMIN_SISTEMA'], 'REABASTECIMIENTO_VER');
/** Roles que ven los catálogos de proveedores y las oportunidades (mismos que {@code /api/catalogos}). */
export const ROLES_CATALOGOS = conPermiso(['ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR', 'ADMIN_SISTEMA'], 'CATALOGO_PROVEEDOR_VER');
/** Roles que ven precios, costos y margen (mismos que {@code /api/ventas}). */
export const ROLES_PRECIOS = conPermiso(['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'AUDITOR'], 'PRECIO_GESTIONAR');
/** Roles que consultan clientes (mismos que {@code GET /api/clientes}). */
export const ROLES_CLIENTES = conPermiso(['CAJERO', 'VENDEDOR', 'SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR'], 'CLIENTE_VER');
/** Roles que usan el punto de venta: cobra el cajero o el supervisor; el vendedor arma pedidos. */
export const ROLES_POS = conPermiso(['CAJERO', 'SUPERVISOR_VENTAS', 'VENDEDOR'], 'VENTA_COTIZAR');
/** Roles que abren y cierran su propio turno de caja (mismos que {@code /api/turnos}). */
export const ROLES_MI_TURNO = conPermiso(['CAJERO', 'SUPERVISOR_VENTAS'], 'TURNO_OPERAR');
/** Roles que entran a Ventas (mismos que {@code GET /api/ventas}; el cajero solo busca por consecutivo para anular o devolver). */
export const ROLES_VENTAS = conPermiso(['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR', 'VENDEDOR', 'CAJERO'], 'VENTA_VER');
/** Roles que ven los reportes de ventas (mismos que {@code GET /api/reportes/ventas}). */
export const ROLES_REPORTES_VENTAS = conPermiso(['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR', 'VENDEDOR', 'CAJERO'], 'REPORTE_VENTAS');
/** Spec 032: entran a Domicilios quien los gestiona, quien los reparte y quien cobra la contraentrega. */
export const ROLES_DOMICILIOS = conPermiso(['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'CAJERO'],
  ['DOMICILIO_GESTIONAR', 'DOMICILIO_REPARTIR', 'VENTA_COBRAR']);
/** Roles que ven los pedidos de venta (mismos que {@code /api/pedidos-venta}). */
export const ROLES_PEDIDOS_VENTA = conPermiso(['VENDEDOR', 'CAJERO', 'SUPERVISOR_VENTAS', 'ADMIN_SISTEMA'], 'PEDIDO_VENTA_GESTIONAR');
/** Roles que ven la cartera (mismos que {@code GET /api/cartera}). */
export const ROLES_CARTERA = conPermiso(['CAJERO', 'SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR'], 'CARTERA_VER');
/** Roles que ven cajas y turnos (mismos que {@code GET /api/turnos}). */
export const ROLES_CAJA = conPermiso(['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR'], 'TURNO_BUSCAR');
/** Roles que ven el tablero del dueño (mismos que {@code GET /api/reportes/tablero}). */
export const ROLES_TABLERO = conPermiso(['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'AUDITOR'], 'TABLERO_VER');
/** Roles que leen los reportes de gasto. */
export const ROLES_REPORTES = conPermiso(['AUDITOR', 'ADMIN_COMPRAS', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO'], 'REPORTE_COMPRAS');
/** Roles que leen proveedores. */
export const ROLES_PROVEEDORES = conPermiso(['ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR'], 'PROVEEDOR_VER');
/** Roles internos (todos menos PROVEEDOR). */
export const INTERNOS = soloInternos(['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'SOLICITANTE', 'COMPRADOR', 'APROBADOR', 'ALMACENISTA', 'CAJERO', 'SUPERVISOR_VENTAS', 'VENDEDOR', 'ADMINISTRATIVO', 'AUDITOR']);
/** Roles que entran a Configuración (la unión de quienes ven contratos y quienes ven usuarios). */
export const ROLES_CONFIGURACION = conPermiso(['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'AUDITOR', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO'], ['CONTRATO_VER', 'USUARIO_VER']);
/** Configuración más bodega: los catálogos base. */
export const ROLES_CATALOGOS_BASE = conPermiso(['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'AUDITOR', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO', 'ALMACENISTA'], ['CONTRATO_VER', 'USUARIO_VER', 'DESPACHO_VER']);
/** Quien ve usuarios y roles. */
export const ROLES_USUARIOS = conPermiso(['ADMIN_SISTEMA', 'AUDITOR'], 'USUARIO_VER');
/** Quien consulta la auditoría. */
export const ROLES_AUDITORIA = conPermiso(['AUDITOR', 'ADMIN_SISTEMA'], 'AUDITORIA_VER');
/** Quien ve los informes de compras. */
export const ROLES_INFORMES = conPermiso(['ADMIN_SISTEMA', 'ADMIN_COMPRAS'], 'INFORME_COMPRAS_VER');
/** Quien configura avisos y correos. */
export const ROLES_NOTIFICACIONES = conPermiso(['ADMIN_SISTEMA'], 'NOTIFICACIONES_CONFIGURAR');
/** Quien edita los datos de la empresa. */
export const ROLES_EMPRESA = conPermiso(['ADMIN_SISTEMA'], 'EMPRESA_GESTIONAR');
/** Quien importa datos (productos y precios). */
export const ROLES_IMPORTACION = conPermiso(['ADMIN_SISTEMA', 'ADMIN_COMPRAS'], 'CATALOGO_BASE_GESTIONAR');
/** Quien crea solicitudes de compra. */
export const ROLES_SOLICITAR = conPermiso(['SOLICITANTE'], 'SOLICITUD_GESTIONAR');
/** Quien crea RFQ. */
export const ROLES_CREAR_RFQ = conPermiso(['COMPRADOR'], 'RFQ_GESTIONAR');
/** Quien emite órdenes de compra. */
export const ROLES_EMITIR_ORDEN = conPermiso(['COMPRADOR'], 'ORDEN_GESTIONAR');

/**
 * Menú de la empresa agrupado por categorías (spec 011). Inicio va sola y sin título.
 * Los roles y módulos de cada opción son los mismos que exige el backend.
 */
export const MENU: SeccionMenu[] = [
  {
    titulo: '',
    opciones: [
      { texto: 'Inicio', icono: 'home', ruta: '/app/inicio' },
    ],
  },
  {
    titulo: 'Ventas',
    icono: 'point_of_sale',
    opciones: [
      { texto: 'Punto de venta', icono: 'shopping_cart_checkout', ruta: '/app/pos', roles: ROLES_POS, modulo: 'VENTAS' },
      { texto: 'Mi turno', icono: 'schedule', ruta: '/app/caja/mi-turno', roles: ROLES_MI_TURNO, modulo: 'CAJA' },
      { texto: 'Pedidos de venta', icono: 'receipt_long', ruta: '/app/pedidos-venta', roles: ROLES_PEDIDOS_VENTA, modulo: 'VENTAS' },
      { texto: 'Ventas', icono: 'point_of_sale', ruta: '/app/ventas', roles: ROLES_VENTAS, modulo: 'VENTAS', exacta: true },
      { texto: 'Domicilios', icono: 'two_wheeler', ruta: '/app/domicilios', roles: ROLES_DOMICILIOS, modulo: 'VENTAS' },
      { texto: 'Clientes', icono: 'groups', ruta: '/app/clientes', roles: ROLES_CLIENTES, modulo: 'VENTAS' },
      { texto: 'Precios y margen', icono: 'sell', ruta: '/app/precios', roles: ROLES_PRECIOS, modulo: 'VENTAS' },
      { texto: 'Cartera', icono: 'account_balance_wallet', ruta: '/app/cartera', roles: ROLES_CARTERA, modulo: 'CARTERA' },
      { texto: 'Cajas y turnos', icono: 'storefront', ruta: '/app/caja', roles: ROLES_CAJA, modulo: 'CAJA', exacta: true },
    ],
  },
  {
    titulo: 'Compras',
    icono: 'shopping_cart',
    opciones: [
      { texto: 'Solicitudes', icono: 'description', ruta: '/app/solicitudes', modulo: 'COMPRAS', contador: 'solicitudes' },
      { texto: 'Pedidos internos', icono: 'campaign', ruta: '/app/pedidos', roles: INTERNOS, modulo: 'COMPRAS' },
      { texto: 'Cotizaciones con IA', icono: 'auto_awesome', ruta: '/app/cotizaciones', roles: ROLES_RFQ, modulo: 'RFQ_COTIZACIONES' },
      { texto: 'Adjudicaciones', icono: 'fact_check', ruta: '/app/adjudicaciones', roles: INTERNOS, modulo: 'RFQ_COTIZACIONES' },
      { texto: 'Órdenes y pagos', icono: 'receipt_long', ruta: '/app/ordenes', roles: ROLES_ORDENES, modulo: 'ORDENES_PAGOS' },
      { texto: 'Pagos', icono: 'payments', ruta: '/app/pagos', roles: ROLES_PAGOS, modulo: 'ORDENES_PAGOS' },
      { texto: 'Recepción', icono: 'local_shipping', ruta: '/app/recepcion', roles: ROLES_RECEPCION, modulo: 'RECEPCION' },
      { texto: 'Proveedores', icono: 'group', ruta: '/app/proveedores', roles: ROLES_PROVEEDORES, modulo: 'PROVEEDORES' },
      { texto: 'Catálogos de proveedores', icono: 'storefront', ruta: '/app/catalogos', roles: ROLES_CATALOGOS, modulo: 'PROVEEDORES' },
    ],
  },
  {
    titulo: 'Inventario',
    icono: 'inventory_2',
    opciones: [
      { texto: 'Productos y stock', icono: 'inventory_2', ruta: '/app/inventario', modulo: 'INVENTARIO', contador: 'bajoMinimo', exacta: true },
      { texto: 'Movimientos de stock', icono: 'swap_horiz', ruta: '/app/inventario/movimientos', modulo: 'INVENTARIO' },
      { texto: 'Despachos', icono: 'outbox', ruta: '/app/despachos', roles: ROLES_DESPACHOS, modulo: 'DESPACHOS' },
      { texto: 'Plan de reabastecimiento', icono: 'event_repeat', ruta: '/app/inventario/reabastecimiento', roles: ROLES_REABASTECIMIENTO, modulo: 'COMPRAS' },
    ],
  },
  {
    titulo: 'Reportes',
    icono: 'bar_chart',
    opciones: [
      { texto: 'Tablero del dueño', icono: 'monitoring', ruta: '/app/tablero', roles: ROLES_TABLERO, modulo: 'REPORTES' },
      { texto: 'Reportes de compras', icono: 'bar_chart', ruta: '/app/reportes', roles: ROLES_REPORTES, modulo: 'REPORTES', exacta: true },
      { texto: 'Reportes de ventas', icono: 'query_stats', ruta: '/app/reportes/ventas', roles: ROLES_REPORTES_VENTAS, modulo: 'VENTAS' },
      { texto: 'Rotación de productos', icono: 'trending_up', ruta: '/app/inventario/rotacion', roles: ROLES_ROTACION, modulo: 'REPORTES' },
      { texto: 'Informe de compras', icono: 'summarize', ruta: '/app/informes', roles: ROLES_INFORMES, modulo: 'COMPRAS' },
    ],
  },
  {
    titulo: 'Gestión humana',
    icono: 'groups',
    opciones: [
      { texto: 'Usuarios y roles', icono: 'apartment', ruta: '/app/usuarios', roles: ROLES_USUARIOS },
      { texto: 'Delegaciones', icono: 'group_add', ruta: '/app/delegaciones', roles: INTERNOS, modulo: 'COMPRAS' },
    ],
  },
  {
    titulo: 'Gestión',
    icono: 'settings',
    opciones: [
      { texto: 'Configuración', icono: 'settings', ruta: '/app/configuracion', roles: ROLES_CONFIGURACION, exacta: true },
      { texto: 'Avisos y correos', icono: 'notifications', ruta: '/app/configuracion/notificaciones', roles: ROLES_NOTIFICACIONES },
      { texto: 'Auditoría', icono: 'shield', ruta: '/app/auditoria', roles: ROLES_AUDITORIA, modulo: 'AUDITORIA' },
    ],
  },
];

/**
 * Secciones del menú con solo las opciones que el usuario puede usar; quita las que quedan vacías.
 * La usan la barra lateral y la página de Inicio para que muestren siempre lo mismo.
 *
 * @param tieneRol dice si el usuario tiene alguno de los roles (sin roles = cualquiera)
 * @return secciones visibles para el usuario, en el orden del menú
 */
export function seccionesVisibles(tieneRol: (roles?: readonly string[]) => boolean): SeccionMenu[] {
  return MENU
    .map(s => ({ ...s, opciones: s.opciones.filter(o => tieneRol(o.roles)) }))
    .filter(s => s.opciones.length > 0);
}

/**
 * Categoría a la que pertenece una pantalla (spec 014): la de la opción cuya ruta coincide más largo con la dirección,
 * también en sus subpáginas ({@code /app/ordenes/15} es de Compras). Así {@code /app/inventario/rotacion} es de
 * Reportes y {@code /app/inventario} es de Inventario.
 *
 * @param secciones secciones visibles para el usuario
 * @param url dirección actual (puede traer {@code ?} o {@code #})
 * @return la categoría con título que contiene la pantalla, o {@code null} en Inicio o fuera del menú
 */
export function seccionDeRuta(secciones: readonly SeccionMenu[], url: string): SeccionMenu | null {
  const ruta = url.split(/[?#]/)[0];
  let mejor: { seccion: SeccionMenu; largo: number } | null = null;
  for (const s of secciones) {
    for (const o of s.opciones) {
      if ((ruta === o.ruta || ruta.startsWith(o.ruta + '/')) && (!mejor || o.ruta.length > mejor.largo)) {
        mejor = { seccion: s, largo: o.ruta.length };
      }
    }
  }
  return mejor?.seccion.titulo ? mejor.seccion : null;
}
