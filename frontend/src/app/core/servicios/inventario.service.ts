import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Bodega, CambioVariantes, CargaMasiva, Categoria, CorreoExportacion, Movimiento, MovimientoNuevo, RegistroMovimiento, StockBodega, InventarioResumen, Pagina, Producto, ProductoConVariantes, ProductoEnSede, ProductoNuevo, Proveedor, Reabastecimiento, ResumenSede, Solicitud, SugerenciaReorden, UnidadMedida, Variantes,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Filtros del kardex; los vacíos no se envían. */
export interface FiltroMovimientos {
  productoId?: number | null;
  bodegaId?: number | null;
  tipo?: string | null;
  desde?: string | null;
  hasta?: string | null;
}

/** Productos, stock, reorden sugerida y carga masiva. */
@Injectable({ providedIn: 'root' })
export class InventarioService {
  private readonly http = inject(HttpClient);

  /**
   * Resumen del inventario con valor y estado de cada producto (reporte de solo lectura).
   *
   * @param categoriaId    filtro opcional
   * @param soloBajoMinimo solo los que están bajo el mínimo
   */
  resumen(categoriaId: number | null, soloBajoMinimo: boolean): Observable<InventarioResumen> {
    return this.http.get<InventarioResumen>(api('/api/reportes/inventario'), { params: parametros({ categoriaId, soloBajoMinimo }) });
  }

  /**
   * @param filtros texto, categoría y estado (por defecto solo los activos); {@code agrupadores: false} deja fuera los
   *                productos generales con variantes (spec 028), para elegir lo que se compra, mueve o vende
   */
  productos(filtros: { texto?: string | null; categoriaId?: number | null; tamano?: number; activo?: boolean; agrupadores?: boolean }): Observable<Pagina<Producto>> {
    return this.http.get<Pagina<Producto>>(api('/api/productos'), {
      params: parametros({ texto: filtros.texto, categoriaId: filtros.categoriaId, activo: filtros.activo ?? true, agrupadores: filtros.agrupadores,
        size: filtros.tamano ?? 100, sort: 'nombre,asc' }),
    });
  }

  /** @param datos producto general con sus atributos y una fila por combinación (spec 028) */
  crearConVariantes(datos: ProductoConVariantes): Observable<Variantes> {
    return this.http.post<Variantes>(api('/api/productos/con-variantes'), datos);
  }

  /** @param id producto general; sus atributos y sus variantes con su stock (spec 028) */
  variantes(id: number): Observable<Variantes> {
    return this.http.get<Variantes>(api(`/api/productos/${id}/variantes`));
  }

  /** @param id producto general; @param datos valores nuevos y los datos de las combinaciones que faltan (spec 028) */
  cambiarVariantes(id: number, datos: CambioVariantes): Observable<Variantes> {
    return this.http.put<Variantes>(api(`/api/productos/${id}/variantes`), datos);
  }

  /** @param id producto; detalle para editarlo */
  producto(id: number): Observable<Producto> {
    return this.http.get<Producto>(api(`/api/productos/${id}`));
  }

  /** @param id producto; @param datos todos los campos del formulario; el stock no se toca desde aquí */
  actualizarProducto(id: number, datos: ProductoNuevo & { equivalenciaUnidad?: number }): Observable<Producto> {
    return this.http.put<Producto>(api(`/api/productos/${id}`), datos);
  }

  /** @param id producto; @param activar {@code false} lo desactiva (deja de ofrecerse; el historial se conserva) */
  cambiarEstadoProducto(id: number, activar: boolean): Observable<Producto> {
    return this.http.patch<Producto>(api(`/api/productos/${id}/${activar ? 'activar' : 'desactivar'}`), {});
  }

  /** Productos bajo su stock mínimo. */
  alertas(): Observable<Producto[]> {
    return this.http.get<Producto[]>(api('/api/productos/alertas-stock'));
  }

  /** @param producto datos del producto nuevo */
  crear(producto: ProductoNuevo): Observable<Producto> {
    return this.http.post<Producto>(api('/api/productos'), producto);
  }

  categorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(api('/api/categorias'), { params: parametros({ activo: true }) });
  }

  /**
   * @param nombre   categoría nueva, creada desde el formulario del producto (spec 026)
   * @param magnitud qué mide su material (spec 038); vacío = no lo dice
   */
  crearCategoria(nombre: string, magnitud?: Categoria['magnitud'] | null): Observable<Categoria> {
    return this.http.post<Categoria>(api('/api/categorias'), { nombre, magnitud: magnitud || undefined });
  }

  /** @param c categoría con su nombre y lo que mide (spec 038) */
  actualizarCategoria(c: Categoria): Observable<Categoria> {
    return this.http.put<Categoria>(api(`/api/categorias/${c.id}`), { nombre: c.nombre, magnitud: c.magnitud || undefined });
  }

  /** Proveedores habilitados para elegir el del costo de referencia (spec 026). */
  proveedoresHabilitados(): Observable<Pagina<Proveedor>> {
    return this.http.get<Pagina<Proveedor>>(api('/api/proveedores'),
      { params: parametros({ estado: 'HABILITADO', size: 100, sort: 'razonSocial,asc' }) });
  }

  /** @param datos alta corta de un proveedor desde el producto (spec 026): solo el nombre es obligatorio */
  crearProveedor(datos: { razonSocial: string; nit?: string; correo?: string; telefono?: string }): Observable<Proveedor> {
    return this.http.post<Proveedor>(api('/api/proveedores'), datos);
  }

  /** Productos por encima de su stock máximo (spec 026, D17). */
  sobreMaximo(): Observable<Producto[]> {
    return this.http.get<Producto[]>(api('/api/productos/sobre-maximo'));
  }

  unidades(): Observable<UnidadMedida[]> {
    return this.http.get<UnidadMedida[]>(api('/api/unidades-medida'), { params: parametros({ activo: true }) });
  }

  /** Cuánto pedir por producto (lo calcula el backend: máximo − stock − por recibir − ya solicitado). */
  sugerencias(): Observable<SugerenciaReorden[]> {
    return this.http.get<SugerenciaReorden[]>(api('/api/reorden/sugerencias'));
  }

  /**
   * Plan de reabastecimiento: qué pedir, cuándo y cuánto según el consumo y el plazo de entrega (lo calcula el backend).
   *
   * @param sedeId sede (vacío = toda la empresa)
   */
  reabastecimiento(sedeId: number | null = null): Observable<Reabastecimiento> {
    return this.http.get<Reabastecimiento>(api('/api/reabastecimiento'), { params: parametros({ sedeId }) });
  }

  /** Comparación de sedes: productos por pedir y traslados posibles en cada una (lo calcula el backend). */
  resumenSedes(): Observable<ResumenSede[]> {
    return this.http.get<ResumenSede[]>(api('/api/reabastecimiento/sedes'));
  }

  /** @param productoId producto; su stock y sus límites en cada sede */
  productoEnSedes(productoId: number): Observable<ProductoEnSede[]> {
    return this.http.get<ProductoEnSede[]>(api(`/api/productos/${productoId}/sedes`));
  }

  /**
   * Fija el mínimo y el máximo de un producto en una sede.
   *
   * @param productoId  producto
   * @param sedeId      sede
   * @param stockMinimo mínimo en la sede (0 = sin mínimo propio)
   * @param stockMaximo máximo en la sede (vacío = sin máximo)
   */
  guardarLimitesSede(productoId: number, sedeId: number, stockMinimo: number, stockMaximo: number | null): Observable<ProductoEnSede> {
    return this.http.put<ProductoEnSede>(api(`/api/productos/${productoId}/sedes/${sedeId}`), { stockMinimo, stockMaximo });
  }

  /**
   * Deja un borrador de solicitud con las cantidades del plan de reabastecimiento.
   *
   * @param fechaRequerida fecha requerida (ISO)
   * @param productoIds    productos elegidos
   * @param sedeId         sede del plan (vacío = toda la empresa)
   */
  borradorReabastecimiento(fechaRequerida: string, productoIds: number[], sedeId: number | null = null): Observable<Solicitud> {
    return this.http.post<Solicitud>(api('/api/reabastecimiento/borrador'), { fechaRequerida, productoIds, sedeId });
  }

  /**
   * Cambia los días hábiles de entrega y de seguridad de un producto (vacío = el de la empresa).
   *
   * @param id               producto
   * @param plazoEntregaDias días hábiles que tarda el proveedor
   * @param diasSeguridad    días hábiles de colchón
   */
  cambiarPlazo(id: number, plazoEntregaDias: number | null, diasSeguridad: number | null): Observable<Producto> {
    return this.http.patch<Producto>(api(`/api/productos/${id}/plazo`), { plazoEntregaDias, diasSeguridad });
  }

  /**
   * Deja un borrador de solicitud con las cantidades sugeridas; el solicitante decide si lo envía.
   *
   * @param fechaRequerida fecha requerida (ISO)
   * @param productoIds    productos elegidos (vacío = todos los sugeridos)
   */
  borradorReorden(fechaRequerida: string, productoIds: number[]): Observable<Solicitud> {
    return this.http.post<Solicitud>(api('/api/reorden/borrador'), { fechaRequerida, productoIds });
  }

  /**
   * Carga masiva por Excel: primero se valida y luego se aplica (todo o nada).
   *
   * @param archivo     .xlsx
   * @param soloValidar {@code true} para revisar sin guardar
   */
  cargaMasiva(archivo: File, soloValidar: boolean): Observable<CargaMasiva> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post<CargaMasiva>(api('/api/productos/carga-masiva'), datos, { params: parametros({ soloValidar }) });
  }

  /** Plantilla de Excel para la carga masiva. */
  plantilla(): Observable<Blob> {
    return this.http.get(api('/api/productos/carga-masiva/plantilla'), { responseType: 'blob' });
  }

  /** Bodegas activas. */
  bodegas(): Observable<Bodega[]> {
    return this.http.get<Bodega[]>(api('/api/bodegas'), { params: parametros({ activo: true }) });
  }

  /** @param bodegaId filtro opcional; existencia de cada producto en cada bodega (las cantidades las calcula el backend) */
  stock(bodegaId?: number | null): Observable<StockBodega[]> {
    return this.http.get<StockBodega[]>(api('/api/inventario/stock'), { params: parametros({ bodegaId }) });
  }

  /**
   * Kardex: movimientos de stock, los más recientes primero.
   *
   * @param f      producto, bodega, tipo y rango de fechas (ISO)
   * @param pagina número de página desde 0
   */
  movimientos(f: FiltroMovimientos, pagina: number): Observable<Pagina<Movimiento>> {
    return this.http.get<Pagina<Movimiento>>(api('/api/inventario/movimientos'),
      { params: parametros({ ...f, page: pagina, size: 25, sort: 'creadoEn,desc' }) });
  }

  /** @param datos salida por consumo, ajuste o traslado; el stock solo cambia por movimientos con motivo */
  registrarMovimiento(datos: MovimientoNuevo): Observable<RegistroMovimiento> {
    return this.http.post<RegistroMovimiento>(api('/api/inventario/movimientos'), datos);
  }

  /** @param f producto (obligatorio), bodega, tipo y fechas; kardex en Excel */
  kardexExcel(f: FiltroMovimientos): Observable<Blob> {
    return this.http.get(api('/api/exportaciones/inventario/kardex'), { params: parametros({ ...f, formato: 'xlsx' }), responseType: 'blob' });
  }

  /** @param f mismos filtros del Excel; lo envía al correo del usuario de la sesión */
  kardexPorCorreo(f: FiltroMovimientos): Observable<CorreoExportacion> {
    return this.http.post<CorreoExportacion>(api('/api/exportaciones/inventario/kardex/correo'), {}, { params: parametros({ ...f, formato: 'xlsx' }) });
  }

  /** Envía las existencias en Excel al correo del usuario de la sesión. */
  stockPorCorreo(): Observable<CorreoExportacion> {
    return this.http.post<CorreoExportacion>(api('/api/exportaciones/inventario/stock/correo'), {}, { params: parametros({ formato: 'xlsx' }) });
  }

  /** Existencias en Excel. */
  exportarStock(): Observable<Blob> {
    return this.http.get(api('/api/exportaciones/inventario/stock'), { params: parametros({ formato: 'xlsx' }), responseType: 'blob' });
  }
}
