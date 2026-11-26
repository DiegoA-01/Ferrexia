package com.proyecto.supply_core.security.context;

/**
 * Códigos de permiso por acción (spec 022, sembrados en V43). Cada endpoint protegido lleva
 * {@code @RequierePermiso} con uno de estos códigos; los roles los reciben en {@code rol_permiso} y una persona
 * puede sumar o quitar permisos sueltos en {@code usuario_permiso}. Usar siempre estas constantes, nunca texto suelto.
 */
public final class Permisos {

    // ----------------------------------------------------------------- compras
    /** Adjudicar una RFQ. */
    public static final String ADJUDICACION_CREAR = "ADJUDICACION_CREAR";
    /** Ver adjudicaciones. */
    public static final String ADJUDICACION_VER = "ADJUDICACION_VER";
    /** Generar, publicar y explicar el análisis. */
    public static final String ANALISIS_GENERAR = "ANALISIS_GENERAR";
    /** Ver el análisis de cotizaciones. */
    public static final String ANALISIS_VER = "ANALISIS_VER";
    /** Confirmar equivalencias de catálogos. */
    public static final String CATALOGO_PROVEEDOR_DECIDIR = "CATALOGO_PROVEEDOR_DECIDIR";
    /** Ver catálogos de proveedores y oportunidades. */
    public static final String CATALOGO_PROVEEDOR_VER = "CATALOGO_PROVEEDOR_VER";
    /** Crear, cambiar vigencia y anular contratos. */
    public static final String CONTRATO_GESTIONAR = "CONTRATO_GESTIONAR";
    /** Consultar ofertas de contratos al comprar. */
    public static final String CONTRATO_OFERTAS = "CONTRATO_OFERTAS";
    /** Ver contratos y precios pactados. */
    public static final String CONTRATO_VER = "CONTRATO_VER";
    /** Confirmar o digitar la lectura de cotizaciones. */
    public static final String COTIZACION_LECTURA_CONFIRMAR = "COTIZACION_LECTURA_CONFIRMAR";
    /** Ver la lectura de cotizaciones con IA. */
    public static final String COTIZACION_LECTURA_VER = "COTIZACION_LECTURA_VER";
    /** Ver cotizaciones recibidas. */
    public static final String COTIZACION_VER = "COTIZACION_VER";
    /** Anular facturas de proveedores. */
    public static final String FACTURA_ANULAR = "FACTURA_ANULAR";
    /** Aprobar facturas con diferencias. */
    public static final String FACTURA_APROBAR = "FACTURA_APROBAR";
    /** Buscar facturas de proveedores. */
    public static final String FACTURA_BUSCAR = "FACTURA_BUSCAR";
    /** Registrar facturas de proveedores. */
    public static final String FACTURA_REGISTRAR = "FACTURA_REGISTRAR";
    /** Ver facturas de proveedores de una orden. */
    public static final String FACTURA_VER = "FACTURA_VER";
    /** Preparar y enviar informes de compras. */
    public static final String INFORME_COMPRAS_ENVIAR = "INFORME_COMPRAS_ENVIAR";
    /** Pedir informes de compras. */
    public static final String INFORME_COMPRAS_PEDIR = "INFORME_COMPRAS_PEDIR";
    /** Ver informes de compras. */
    public static final String INFORME_COMPRAS_VER = "INFORME_COMPRAS_VER";
    /** Emitir, versionar y cancelar órdenes de compra. */
    public static final String ORDEN_GESTIONAR = "ORDEN_GESTIONAR";
    /** Ver órdenes de compra. */
    public static final String ORDEN_VER = "ORDEN_VER";
    /** Buscar pagos a proveedores. */
    public static final String PAGO_BUSCAR = "PAGO_BUSCAR";
    /** Registrar y anular pagos a proveedores. */
    public static final String PAGO_REGISTRAR = "PAGO_REGISTRAR";
    /** Ver pagos de una orden. */
    public static final String PAGO_VER = "PAGO_VER";
    /** Gestionar pedidos internos. */
    public static final String PEDIDO_INTERNO_GESTIONAR = "PEDIDO_INTERNO_GESTIONAR";
    /** Cambiar los pesos de evaluación. */
    public static final String PESOS_EVALUACION_GESTIONAR = "PESOS_EVALUACION_GESTIONAR";
    /** Administrar políticas de aprobación. */
    public static final String POLITICA_APROBACION_GESTIONAR = "POLITICA_APROBACION_GESTIONAR";
    /** Crear y editar presupuestos. */
    public static final String PRESUPUESTO_GESTIONAR = "PRESUPUESTO_GESTIONAR";
    /** Ver presupuestos por centro de costo. */
    public static final String PRESUPUESTO_VER = "PRESUPUESTO_VER";
    /** Crear solicitudes desde el reabastecimiento. */
    public static final String REABASTECIMIENTO_SOLICITAR = "REABASTECIMIENTO_SOLICITAR";
    /** Ver el plan de reabastecimiento. */
    public static final String REABASTECIMIENTO_VER = "REABASTECIMIENTO_VER";
    /** Cerrar recepciones. */
    public static final String RECEPCION_CERRAR = "RECEPCION_CERRAR";
    /** Registrar recepciones de mercancía. */
    public static final String RECEPCION_REGISTRAR = "RECEPCION_REGISTRAR";
    /** Resolver excedentes recibidos. */
    public static final String RECEPCION_RESOLVER_EXCESO = "RECEPCION_RESOLVER_EXCESO";
    /** Ver recepciones de mercancía. */
    public static final String RECEPCION_VER = "RECEPCION_VER";
    /** Crear solicitudes desde la reorden. */
    public static final String REORDEN_SOLICITAR = "REORDEN_SOLICITAR";
    /** Ver sugerencias de reorden. */
    public static final String REORDEN_VER = "REORDEN_VER";
    /** Crear, abrir, cerrar y cancelar RFQ. */
    public static final String RFQ_GESTIONAR = "RFQ_GESTIONAR";
    /** Ver solicitudes de cotización (RFQ). */
    public static final String RFQ_VER = "RFQ_VER";
    /** Cancelar solicitudes de compra. */
    public static final String SOLICITUD_CANCELAR = "SOLICITUD_CANCELAR";
    /** Crear, editar y enviar solicitudes de compra. */
    public static final String SOLICITUD_GESTIONAR = "SOLICITUD_GESTIONAR";
    // ----------------------------------------------------------------- proveedores
    /** Habilitar, editar y suspender proveedores. */
    public static final String PROVEEDOR_GESTIONAR = "PROVEEDOR_GESTIONAR";
    /** Ver proveedores y evaluaciones. */
    public static final String PROVEEDOR_VER = "PROVEEDOR_VER";
    // ----------------------------------------------------------------- portal
    /** Usar el portal del proveedor. */
    public static final String PORTAL_PROVEEDOR = "PORTAL_PROVEEDOR";
    // ----------------------------------------------------------------- inventario
    /** Administrar categorías y unidades de medida. */
    public static final String CATALOGO_BASE_GESTIONAR = "CATALOGO_BASE_GESTIONAR";
    /** Anular despachos. */
    public static final String DESPACHO_ANULAR = "DESPACHO_ANULAR";
    /** Crear y confirmar despachos. */
    public static final String DESPACHO_GESTIONAR = "DESPACHO_GESTIONAR";
    /** Ver despachos. */
    public static final String DESPACHO_VER = "DESPACHO_VER";
    /** Registrar movimientos de inventario. */
    public static final String INVENTARIO_MOVER = "INVENTARIO_MOVER";
    /** Crear y editar productos. */
    public static final String PRODUCTO_GESTIONAR = "PRODUCTO_GESTIONAR";
    // ----------------------------------------------------------------- ventas
    /** Crear y editar clientes. */
    public static final String CLIENTE_GESTIONAR = "CLIENTE_GESTIONAR";
    /** Ver clientes. */
    public static final String CLIENTE_VER = "CLIENTE_VER";
    /** Recibir devoluciones. */
    public static final String DEVOLUCION_REGISTRAR = "DEVOLUCION_REGISTRAR";
    /** Ver devoluciones. */
    public static final String DEVOLUCION_VER = "DEVOLUCION_VER";
    /** Armar, editar y descartar pedidos de venta. */
    public static final String PEDIDO_VENTA_GESTIONAR = "PEDIDO_VENTA_GESTIONAR";
    /** Administrar precios, listas e impuestos. */
    public static final String PRECIO_GESTIONAR = "PRECIO_GESTIONAR";
    /** Ver listas de precios. */
    public static final String PRECIO_LISTAS_VER = "PRECIO_LISTAS_VER";
    /** Buscar productos para vender. */
    public static final String VENTA_BUSCAR_PRODUCTO = "VENTA_BUSCAR_PRODUCTO";
    /** Cobrar y anular ventas. */
    public static final String VENTA_COBRAR = "VENTA_COBRAR";
    /** Cotizar un carrito. */
    public static final String VENTA_COTIZAR = "VENTA_COTIZAR";
    /** Cambiar los límites de descuento. */
    public static final String VENTA_LIMITES_GESTIONAR = "VENTA_LIMITES_GESTIONAR";
    /** Ver los límites de descuento. */
    public static final String VENTA_LIMITES_VER = "VENTA_LIMITES_VER";
    /** Ver ventas y reimprimir tiquetes. */
    public static final String VENTA_VER = "VENTA_VER";
    // ----------------------------------------------------------------- caja
    /** Pedir la autorización de un supervisor con PIN. */
    public static final String AUTORIZACION_PEDIR = "AUTORIZACION_PEDIR";
    /** Tener PIN de supervisor. */
    public static final String AUTORIZACION_PIN = "AUTORIZACION_PIN";
    /** Administrar categorías de ingresos y egresos. */
    public static final String CAJA_CATEGORIAS_GESTIONAR = "CAJA_CATEGORIAS_GESTIONAR";
    /** Ver categorías de ingresos y egresos. */
    public static final String CAJA_CATEGORIAS_VER = "CAJA_CATEGORIAS_VER";
    /** Ver la configuración de caja. */
    public static final String CAJA_CONFIGURACION_VER = "CAJA_CONFIGURACION_VER";
    /** Crear y configurar cajas. */
    public static final String CAJA_GESTIONAR = "CAJA_GESTIONAR";
    /** Registrar retiros a la bóveda sin el PIN del supervisor (spec 025, D15). */
    public static final String CAJA_RETIRO_SIN_PIN = "CAJA_RETIRO_SIN_PIN";
    /** Registrar gastos de la caja sin el PIN del supervisor (spec 025, D15). */
    public static final String CAJA_GASTO_SIN_PIN = "CAJA_GASTO_SIN_PIN";
    /** Registrar ingresos de efectivo sin el PIN del supervisor (spec 025, D15). */
    public static final String CAJA_INGRESO_SIN_PIN = "CAJA_INGRESO_SIN_PIN";
    /** Ver las cajas. */
    public static final String CAJA_VER = "CAJA_VER";
    /** Buscar turnos de todas las cajas. */
    public static final String TURNO_BUSCAR = "TURNO_BUSCAR";
    /** Abrir, mover efectivo y cerrar su turno. */
    public static final String TURNO_OPERAR = "TURNO_OPERAR";
    /** Aprobar diferencias y ver lo esperado de caja. */
    public static final String TURNO_SUPERVISAR = "TURNO_SUPERVISAR";
    /** Ver el detalle de un turno. */
    public static final String TURNO_VER = "TURNO_VER";
    // ----------------------------------------------------------------- cartera
    /** Recibir abonos de clientes. */
    public static final String CARTERA_ABONAR = "CARTERA_ABONAR";
    /** Dar excepciones de crédito. */
    public static final String CARTERA_EXCEPCION = "CARTERA_EXCEPCION";
    /** Ver la cartera de clientes. */
    public static final String CARTERA_VER = "CARTERA_VER";
    // ----------------------------------------------------------------- reportes
    /** Marcar cuadres diarios como revisados. */
    public static final String CUADRE_REVISAR = "CUADRE_REVISAR";
    /** Exportar clientes. */
    public static final String EXPORTAR_CLIENTES = "EXPORTAR_CLIENTES";
    /** Exportar órdenes de compra. */
    public static final String EXPORTAR_ORDEN = "EXPORTAR_ORDEN";
    /** Ver reportes de caja. */
    public static final String REPORTE_CAJA = "REPORTE_CAJA";
    /** Ver reportes de gasto y proveedores. */
    public static final String REPORTE_COMPRAS = "REPORTE_COMPRAS";
    /** Ver el reporte de inventario. */
    public static final String REPORTE_INVENTARIO = "REPORTE_INVENTARIO";
    /** Ver la rotación de inventario. */
    public static final String REPORTE_ROTACION = "REPORTE_ROTACION";
    /** Ver reportes de ventas. */
    public static final String REPORTE_VENTAS = "REPORTE_VENTAS";
    /** Ver tablero de ventas y cuadres diarios. */
    public static final String REPORTE_VENTAS_DUENO = "REPORTE_VENTAS_DUENO";
    /** Ver el tablero de compras. */
    public static final String TABLERO_VER = "TABLERO_VER";
    // ----------------------------------------------------------------- configuracion
    /** Administrar bodegas. */
    public static final String BODEGA_GESTIONAR = "BODEGA_GESTIONAR";
    /** Administrar centros de costo. */
    public static final String CENTRO_COSTO_GESTIONAR = "CENTRO_COSTO_GESTIONAR";
    /** Editar los datos de la empresa. */
    public static final String EMPRESA_GESTIONAR = "EMPRESA_GESTIONAR";
    /** Configurar notificaciones de la empresa. */
    public static final String NOTIFICACIONES_CONFIGURAR = "NOTIFICACIONES_CONFIGURAR";
    /** Administrar sedes. */
    public static final String SEDE_GESTIONAR = "SEDE_GESTIONAR";
    // ----------------------------------------------------------------- usuarios
    /** Ver roles. */
    public static final String ROL_VER = "ROL_VER";
    /** Crear, editar y desactivar usuarios, restablecer claves y MFA. */
    public static final String USUARIO_GESTIONAR = "USUARIO_GESTIONAR";
    /** Ver usuarios. */
    public static final String USUARIO_VER = "USUARIO_VER";
    // ----------------------------------------------------------------- auditoria
    /** Consultar la auditoría. */
    public static final String AUDITORIA_VER = "AUDITORIA_VER";

    // ----------------------------------------------------------------- V44 (Parte B)
    /** Crear, editar y borrar roles propios de la empresa. */
    public static final String ROL_GESTIONAR = "ROL_GESTIONAR";
    /** Ver las ventas y los reportes de todos los cajeros y vendedores. */
    public static final String VENTA_VER_TODAS = "VENTA_VER_TODAS";
    /** Ver solo las ventas en las que fue el vendedor. */
    public static final String VENTA_VER_PROPIAS = "VENTA_VER_PROPIAS";
    /** Cambiar pedidos de otros y las condiciones de crédito de un cliente. */
    public static final String VENTA_SUPERVISAR = "VENTA_SUPERVISAR";
    /** Ver los pedidos de venta de todos los vendedores. */
    public static final String PEDIDO_VENTA_VER_TODOS = "PEDIDO_VENTA_VER_TODOS";
    /** Adjuntar documentos a las órdenes de compra. */
    public static final String ORDEN_DOCUMENTOS = "ORDEN_DOCUMENTOS";
    /** Ver las solicitudes de compra de todos. */
    public static final String SOLICITUD_VER_TODAS = "SOLICITUD_VER_TODAS";
    /** Cancelar solicitudes de compra de otras personas. */
    public static final String SOLICITUD_CANCELAR_AJENA = "SOLICITUD_CANCELAR_AJENA";
    /** Revocar delegaciones de aprobación de otras personas. */
    public static final String DELEGACION_REVOCAR_AJENA = "DELEGACION_REVOCAR_AJENA";
    /** Ver los pedidos internos de todas las sedes. */
    public static final String PEDIDO_INTERNO_VER_TODOS = "PEDIDO_INTERNO_VER_TODOS";

    /** Clase de constantes: no se instancia. */
    private Permisos() {
    }
}
