import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AgrupacionVenta, CajaReporte, Cuadre, Pagina, TableroVentas, VentaReporte, VentaSinExistencia, VentasTurnoReporte,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Reportes de ventas y caja, bloque de ventas del tablero y cuadre diario (spec 009). Todo lo calcula el backend. */
@Injectable({ providedIn: 'root' })
export class ReportesVentasService {
  private readonly http = inject(HttpClient);

  /** @param desde fecha ISO; @param hasta fecha ISO; @param agrupar agrupación */
  ventas(desde: string | null, hasta: string | null, agrupar: AgrupacionVenta): Observable<VentaReporte> {
    return this.http.get<VentaReporte>(api('/api/reportes/ventas'), { params: parametros({ desde, hasta, agrupar }) });
  }

  /** @param desde fecha ISO; @param hasta fecha ISO */
  caja(desde: string | null, hasta: string | null): Observable<CajaReporte> {
    return this.http.get<CajaReporte>(api('/api/reportes/caja'), { params: parametros({ desde, hasta }) });
  }

  /** @param desde fecha ISO; @param hasta fecha ISO; @param estado PENDIENTE, REGULARIZADA o ANULADA (null = todos) (spec 030) */
  ventasSinExistencia(desde: string | null, hasta: string | null, estado: string | null): Observable<VentaSinExistencia[]> {
    return this.http.get<VentaSinExistencia[]>(api('/api/reportes/ventas-sin-existencia'), { params: parametros({ desde, hasta, estado }) });
  }

  /** @param desde fecha ISO; @param hasta fecha ISO; @param cajaId solo esa caja (null = todas) */
  ventasPorTurno(desde: string | null, hasta: string | null, cajaId: number | null): Observable<VentasTurnoReporte> {
    return this.http.get<VentasTurnoReporte>(api('/api/reportes/caja/turnos'), { params: parametros({ desde, hasta, cajaId }) });
  }

  /** @param desde fecha ISO; @param hasta fecha ISO; @param cajaId solo esa caja (null = todas) */
  exportarVentasPorTurno(desde: string | null, hasta: string | null, cajaId: number | null): Observable<Blob> {
    return this.http.get(api('/api/exportaciones/reportes/caja/turnos'),
      { params: parametros({ formato: 'xlsx', desde, hasta, cajaId }), responseType: 'blob' });
  }

  /** Bloque de ventas del tablero del dueño. */
  tablero(): Observable<TableroVentas> {
    return this.http.get<TableroVentas>(api('/api/reportes/tablero/ventas'));
  }

  /** @param soloDescuadres solo los días que no cuadran; @param pagina página */
  cuadres(soloDescuadres: boolean, pagina = 0): Observable<Pagina<Cuadre>> {
    return this.http.get<Pagina<Cuadre>>(api('/api/reportes/cuadre'), { params: parametros({ soloDescuadres, page: pagina }) });
  }

  /** @param fecha día ISO que se vuelve a revisar */
  revisar(fecha: string): Observable<Cuadre> {
    return this.http.post<Cuadre>(api(`/api/reportes/cuadre/${fecha}`), {});
  }

  /** @param formato xlsx o pdf; @param desde fecha ISO; @param hasta fecha ISO; @param agrupar agrupación */
  exportarVentas(formato: 'xlsx' | 'pdf', desde: string | null, hasta: string | null, agrupar: AgrupacionVenta): Observable<Blob> {
    return this.http.get(api('/api/exportaciones/reportes/ventas'),
      { params: parametros({ formato, desde, hasta, agrupar }), responseType: 'blob' });
  }

  /** @param formato xlsx o pdf; @param desde fecha ISO; @param hasta fecha ISO */
  exportarCaja(formato: 'xlsx' | 'pdf', desde: string | null, hasta: string | null): Observable<Blob> {
    return this.http.get(api('/api/exportaciones/reportes/caja'), { params: parametros({ formato, desde, hasta }), responseType: 'blob' });
  }
}
