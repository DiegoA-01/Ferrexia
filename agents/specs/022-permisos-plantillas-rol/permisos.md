# Catálogo de permisos (spec 022, Parte A)

Sembrado en `V43__permisos_y_plantillas.sql`. Cada endpoint protegido lleva `@RequierePermiso(Permisos.X)` y cada rol
recibe en `rol_permiso` exactamente los permisos que le daban sus `@RequiereRol`. La foto del acceso de antes está en
`backend/src/test/resources/seguridad/acceso-antes-022.tsv` y la prueba
`AislamientoEmpresasTest.cadaRolConservaExactamenteSuAccesoConLosPermisos` la compara con lo de ahora, endpoint por
endpoint y rol por rol.

Cómo se armó: en cada controlador, los endpoints con el mismo conjunto de roles forman un permiso. Así un código
nunca mezcla dos conjuntos de roles distintos y la equivalencia es exacta. 97 permisos, 259 pares rol-permiso.

## Plantillas (decisión D18)

Los 12 roles de siempre quedan como **plantillas del sistema** (`rol.empresa_id` NULL, `es_plantilla`), con el mismo
código (lo usan el JWT, las políticas de aprobación y la delegación). Siete toman el nombre de D18:

| Código | Nombre |
|---|---|
| `ADMIN_SISTEMA` | Dueño |
| `CAJERO` | Cajero |
| `VENDEDOR` | Vendedor |
| `SUPERVISOR_VENTAS` | Supervisor de ventas |
| `ALMACENISTA` | Bodega |
| `COMPRADOR` | Compras |
| `AUDITOR` | Contador/Auditor |

Siguen con su nombre de antes `ADMIN_COMPRAS`, `SOLICITANTE`, `APROBADOR`, `ADMINISTRATIVO` y `PROVEEDOR`. La
plantilla **Administrador** (`ADMINISTRADOR`, = Dueño sin `EMPRESA_GESTIONAR`) y los roles propios llegan en la Parte B
(V44), junto con 10 permisos más: `ROL_GESTIONAR`, `VENTA_VER_TODAS`, `VENTA_VER_PROPIAS`, `VENTA_SUPERVISAR`,
`PEDIDO_VENTA_VER_TODOS`, `ORDEN_DOCUMENTOS`, `SOLICITUD_VER_TODAS`, `SOLICITUD_CANCELAR_AJENA`,
`DELEGACION_REVOCAR_AJENA` y `PEDIDO_INTERNO_VER_TODOS` (107 en total).

Después se sumaron `CAJA_RETIRO_SIN_PIN`, `CAJA_GASTO_SIN_PIN` y `CAJA_INGRESO_SIN_PIN` (spec 025, V47) y
`VENTA_ANULAR_OTRO_TURNO` y `VENTA_CAMBIAR_PAGO` (spec 029, V51; Dueño, Administrador y Supervisor de ventas): 112 en total.

## Compras

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `ADJUDICACION_CREAR` | Adjudicar una RFQ | Compras | AdjudicacionController |
| `ADJUDICACION_VER` | Ver adjudicaciones | Admin. compras, Aprobador, Contador/Auditor, Compras | AdjudicacionController |
| `ANALISIS_GENERAR` | Generar, publicar y explicar el análisis | Compras | AnalisisController |
| `ANALISIS_VER` | Ver el análisis de cotizaciones | Admin. compras, Aprobador, Contador/Auditor, Compras | AnalisisController |
| `CATALOGO_PROVEEDOR_DECIDIR` | Confirmar equivalencias de catálogos | Admin. compras, Compras | CatalogoController |
| `CATALOGO_PROVEEDOR_VER` | Ver catálogos de proveedores y oportunidades | Admin. compras, Dueño, Contador/Auditor, Compras | CatalogoController |
| `CONTRATO_GESTIONAR` | Crear, cambiar vigencia y anular contratos | Admin. compras | ContratoController |
| `CONTRATO_OFERTAS` | Consultar ofertas de contratos al comprar | Admin. compras, Compras | ContratoController |
| `CONTRATO_VER` | Ver contratos y precios pactados | Administrativo, Admin. compras, Aprobador, Contador/Auditor, Compras | ContratoController |
| `COTIZACION_LECTURA_CONFIRMAR` | Confirmar o digitar la lectura de cotizaciones | Admin. compras, Compras | ExtraccionController |
| `COTIZACION_LECTURA_VER` | Ver la lectura de cotizaciones con IA | Admin. compras, Contador/Auditor, Compras | ExtraccionController |
| `COTIZACION_VER` | Ver cotizaciones recibidas | Admin. compras, Aprobador, Contador/Auditor, Compras | CotizacionController |
| `FACTURA_ANULAR` | Anular facturas de proveedores | Administrativo, Admin. compras | FacturaController |
| `FACTURA_APROBAR` | Aprobar facturas con diferencias | Admin. compras | FacturaController |
| `FACTURA_BUSCAR` | Buscar facturas de proveedores | Administrativo, Admin. compras, Contador/Auditor | FacturaController |
| `FACTURA_REGISTRAR` | Registrar facturas de proveedores | Administrativo | FacturaController |
| `FACTURA_VER` | Ver facturas de proveedores de una orden | Administrativo, Admin. compras, Aprobador, Contador/Auditor, Compras | FacturaController |
| `INFORME_COMPRAS_ENVIAR` | Preparar y enviar informes de compras | Admin. compras | InformeComprasController |
| `INFORME_COMPRAS_PEDIR` | Pedir informes de compras | Dueño | InformeComprasController |
| `INFORME_COMPRAS_VER` | Ver informes de compras | Admin. compras, Dueño | InformeComprasController |
| `ORDEN_GESTIONAR` | Emitir, versionar y cancelar órdenes de compra | Compras | OrdenController |
| `ORDEN_VER` | Ver órdenes de compra | Administrativo, Admin. compras, Bodega, Aprobador, Contador/Auditor, Compras | OrdenController |
| `PAGO_BUSCAR` | Buscar pagos a proveedores | Administrativo, Admin. compras, Contador/Auditor | PagoController |
| `PAGO_REGISTRAR` | Registrar y anular pagos a proveedores | Administrativo | PagoController |
| `PAGO_VER` | Ver pagos de una orden | Administrativo, Admin. compras, Aprobador, Contador/Auditor, Compras | PagoController |
| `PEDIDO_INTERNO_GESTIONAR` | Gestionar pedidos internos | Admin. compras, Compras | PedidoInternoController |
| `PESOS_EVALUACION_GESTIONAR` | Cambiar los pesos de evaluación | Admin. compras | PesosController |
| `POLITICA_APROBACION_GESTIONAR` | Administrar políticas de aprobación | Admin. compras | PoliticaAprobacionController |
| `PRESUPUESTO_GESTIONAR` | Crear y editar presupuestos | Admin. compras | PresupuestoController |
| `PRESUPUESTO_VER` | Ver presupuestos por centro de costo | Administrativo, Admin. compras, Aprobador, Contador/Auditor, Compras | PresupuestoController |
| `REABASTECIMIENTO_SOLICITAR` | Crear solicitudes desde el reabastecimiento | Solicitante | ReabastecimientoController |
| `REABASTECIMIENTO_VER` | Ver el plan de reabastecimiento | Admin. compras, Dueño, Bodega, Contador/Auditor, Compras, Solicitante | ReabastecimientoController |
| `RECEPCION_CERRAR` | Cerrar recepciones | Admin. compras, Bodega | RecepcionController |
| `RECEPCION_REGISTRAR` | Registrar recepciones de mercancía | Bodega | RecepcionController |
| `RECEPCION_RESOLVER_EXCESO` | Resolver excedentes recibidos | Admin. compras, Compras | RecepcionController |
| `RECEPCION_VER` | Ver recepciones de mercancía | Administrativo, Admin. compras, Bodega, Aprobador, Contador/Auditor, Compras | RecepcionController |
| `REORDEN_SOLICITAR` | Crear solicitudes desde la reorden | Solicitante | ReordenController |
| `REORDEN_VER` | Ver sugerencias de reorden | Admin. compras, Bodega, Contador/Auditor, Compras, Solicitante | ReordenController |
| `RFQ_GESTIONAR` | Crear, abrir, cerrar y cancelar RFQ | Compras | RfqController |
| `RFQ_VER` | Ver solicitudes de cotización (RFQ) | Admin. compras, Aprobador, Contador/Auditor, Compras | RfqController |
| `SOLICITUD_CANCELAR` | Cancelar solicitudes de compra | Admin. compras, Solicitante | SolicitudController |
| `SOLICITUD_GESTIONAR` | Crear, editar y enviar solicitudes de compra | Solicitante | SolicitudController |

## Proveedores

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `PROVEEDOR_GESTIONAR` | Habilitar, editar y suspender proveedores | Admin. compras | ProveedorController |
| `PROVEEDOR_VER` | Ver proveedores y evaluaciones | Admin. compras, Contador/Auditor, Compras | ProveedorController |

## Portal del proveedor

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `PORTAL_PROVEEDOR` | Usar el portal del proveedor | Proveedor | PortalCatalogoController, PortalCotizacionController, PortalFacturaController, PortalOrdenController, PortalPagoController, PortalProveedorController, PortalRecepcionController, PortalRfqController |

## Inventario

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `CATALOGO_BASE_GESTIONAR` | Administrar categorías y unidades de medida | Admin. compras, Dueño | CategoriaController, UnidadMedidaController |
| `DESPACHO_ANULAR` | Anular despachos | Admin. compras, Bodega | DespachoController |
| `DESPACHO_GESTIONAR` | Crear y confirmar despachos | Bodega | DespachoController |
| `DESPACHO_VER` | Ver despachos | Admin. compras, Bodega, Contador/Auditor, Compras | DespachoController |
| `INVENTARIO_MOVER` | Registrar movimientos de inventario | Bodega | InventarioController |
| `PRODUCTO_GESTIONAR` | Crear y editar productos | Admin. compras | ProductoCargaController, ProductoController, ProductoSedeController |

## Ventas

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `CLIENTE_GESTIONAR` | Crear y editar clientes | Dueño, Cajero, Supervisor, Vendedor | ClienteController |
| `CLIENTE_VER` | Ver clientes | Dueño, Contador/Auditor, Cajero, Supervisor, Vendedor | ClienteController |
| `DEVOLUCION_REGISTRAR` | Recibir devoluciones | Cajero, Supervisor | DevolucionController |
| `DEVOLUCION_VER` | Ver devoluciones | Dueño, Contador/Auditor, Cajero, Supervisor | DevolucionController |
| `PEDIDO_VENTA_GESTIONAR` | Armar, editar y descartar pedidos de venta | Dueño, Cajero, Supervisor, Vendedor | PedidoVentaController |
| `PRECIO_GESTIONAR` | Administrar precios, listas e impuestos | Admin. compras, Dueño, Contador/Auditor | PreciosController |
| `PRECIO_LISTAS_VER` | Ver listas de precios | Admin. compras, Dueño, Contador/Auditor, Cajero, Supervisor, Vendedor | PreciosController |
| `VENTA_BUSCAR_PRODUCTO` | Buscar productos para vender | Dueño, Cajero, Supervisor, Vendedor | PreciosController |
| `VENTA_COBRAR` | Cobrar y anular ventas | Cajero, Supervisor | VentaController |
| `VENTA_COTIZAR` | Cotizar un carrito | Cajero, Supervisor, Vendedor | VentaController |
| `VENTA_LIMITES_GESTIONAR` | Cambiar los límites de descuento | Dueño | VentaController |
| `VENTA_LIMITES_VER` | Ver los límites de descuento | Dueño, Contador/Auditor, Supervisor | VentaController |
| `VENTA_VER` | Ver ventas y reimprimir tiquetes | Dueño, Contador/Auditor, Cajero, Supervisor, Vendedor | VentaController |

## Caja

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `AUTORIZACION_PEDIR` | Pedir la autorización de un supervisor con PIN | Dueño, Cajero, Supervisor | AutorizacionController |
| `AUTORIZACION_PIN` | Tener PIN de supervisor | Dueño, Supervisor | AutorizacionController |
| `CAJA_CATEGORIAS_GESTIONAR` | Administrar categorías de ingresos y egresos | Dueño, Supervisor | CategoriaMovimientoCajaController |
| `CAJA_CATEGORIAS_VER` | Ver categorías de ingresos y egresos | Dueño, Contador/Auditor, Cajero, Supervisor | CategoriaMovimientoCajaController |
| `CAJA_CONFIGURACION_VER` | Ver la configuración de caja | Dueño, Contador/Auditor, Supervisor | CajaController |
| `CAJA_GESTIONAR` | Crear y configurar cajas | Dueño | CajaController |
| `CAJA_VER` | Ver las cajas | Dueño, Contador/Auditor, Cajero, Supervisor | CajaController |
| `TURNO_BUSCAR` | Buscar turnos de todas las cajas | Dueño, Contador/Auditor, Supervisor | TurnoController |
| `TURNO_OPERAR` | Abrir, mover efectivo y cerrar su turno | Cajero, Supervisor | TurnoController |
| `TURNO_SUPERVISAR` | Aprobar diferencias y ver lo esperado de caja | Dueño, Supervisor | TurnoController |
| `TURNO_VER` | Ver el detalle de un turno | Dueño, Contador/Auditor, Cajero, Supervisor | TurnoController |

## Cartera

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `CARTERA_ABONAR` | Recibir abonos de clientes | Cajero, Supervisor | CarteraController |
| `CARTERA_EXCEPCION` | Dar excepciones de crédito | Dueño, Supervisor | CarteraController |
| `CARTERA_VER` | Ver la cartera de clientes | Dueño, Contador/Auditor, Cajero, Supervisor | CarteraController |

## Reportes y exportación

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `CUADRE_REVISAR` | Marcar cuadres diarios como revisados | Dueño | VentaReporteController |
| `EXPORTAR_CLIENTES` | Exportar clientes | Dueño, Contador/Auditor | ExportacionController |
| `EXPORTAR_ORDEN` | Exportar órdenes de compra | Administrativo, Admin. compras, Bodega, Aprobador, Contador/Auditor, Compras | ExportacionController |
| `REPORTE_CAJA` | Ver reportes de caja | Dueño, Contador/Auditor, Supervisor | ExportacionController, VentaReporteController |
| `REPORTE_COMPRAS` | Ver reportes de gasto y proveedores | Administrativo, Admin. compras, Aprobador, Contador/Auditor, Compras | ReporteController |
| `REPORTE_INVENTARIO` | Ver el reporte de inventario | Administrativo, Admin. compras, Bodega, Aprobador, Contador/Auditor, Compras | ReporteController |
| `REPORTE_ROTACION` | Ver la rotación de inventario | Administrativo, Admin. compras, Dueño, Bodega, Aprobador, Contador/Auditor, Compras | RotacionController |
| `REPORTE_VENTAS` | Ver reportes de ventas | Dueño, Contador/Auditor, Cajero, Supervisor, Vendedor | ExportacionController, VentaReporteController |
| `REPORTE_VENTAS_DUENO` | Ver tablero de ventas y cuadres diarios | Dueño, Contador/Auditor | VentaReporteController |
| `TABLERO_VER` | Ver el tablero de compras | Admin. compras, Dueño, Contador/Auditor | TableroController |

## Configuración

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `BODEGA_GESTIONAR` | Administrar bodegas | Dueño | BodegaController |
| `CENTRO_COSTO_GESTIONAR` | Administrar centros de costo | Dueño | CentroCostoController |
| `EMPRESA_GESTIONAR` | Editar los datos de la empresa | Dueño | EmpresaController |
| `NOTIFICACIONES_CONFIGURAR` | Configurar notificaciones de la empresa | Dueño | ConfigNotificacionController |
| `SEDE_GESTIONAR` | Administrar sedes | Dueño | SedeController |

## Usuarios

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `ROL_VER` | Ver roles | Dueño, Contador/Auditor | RolController |
| `USUARIO_GESTIONAR` | Crear, editar y desactivar usuarios, restablecer claves y MFA | Dueño | UsuarioController |
| `USUARIO_VER` | Ver usuarios | Dueño, Contador/Auditor | UsuarioController |

## Auditoría

| Permiso | Qué permite | Roles que lo tienen | Controladores |
|---|---|---|---|
| `AUDITORIA_VER` | Consultar la auditoría | Dueño, Contador/Auditor | AuditoriaController |
