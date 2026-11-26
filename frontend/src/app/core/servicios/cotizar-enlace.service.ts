import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Cotizacion, CotizacionPublica } from '../api/modelos';
import { api } from '../http/api';

/** Datos del formulario de cotización (los mismos del portal). */
export interface CotizacionEnlaceDatos {
  moneda?: string;
  plazoEntregaDias?: number;
  garantiaMeses?: number;
  validezHasta?: string;
  condicionesComerciales?: string;
  lineas: { rfqLineaId: number; cantidadOfertada: number; unidadOfertada: string; factorConversion: number;
    precioUnitarioOfertado: number; impuestoPct: number }[];
}

/**
 * Spec 031: el proveedor cotiza desde su enlace, sin cuenta. Rutas públicas (sin token) con límite de peticiones; el
 * texto del enlace va en la dirección.
 */
@Injectable({ providedIn: 'root' })
export class CotizarEnlaceService {
  private readonly http = inject(HttpClient);

  /** @param token texto del enlace; empresa, RFQ y borrador */
  ver(token: string): Observable<CotizacionPublica> {
    return this.http.get<CotizacionPublica>(api(`/api/publico/cotizar/${encodeURIComponent(token)}`));
  }

  /** @param token texto del enlace; @param datos precios y condiciones (guarda el borrador) */
  guardar(token: string, datos: CotizacionEnlaceDatos): Observable<Cotizacion> {
    return this.http.put<Cotizacion>(api(`/api/publico/cotizar/${encodeURIComponent(token)}`), datos);
  }

  /** @param token texto del enlace; @param archivo PDF, Excel o foto de la cotización */
  subirArchivo(token: string, archivo: File): Observable<Cotizacion> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post<Cotizacion>(api(`/api/publico/cotizar/${encodeURIComponent(token)}/archivo`), datos);
  }

  /**
   * Envía la cotización; el enlace queda cerrado.
   *
   * @param token          texto del enlace
   * @param turnstileToken token del widget anti-bot ({@code null} si no está configurado)
   * @param sitioWeb       campo trampa (vacío para las personas)
   */
  enviar(token: string, turnstileToken: string | null, sitioWeb: string): Observable<void> {
    return this.http.post<void>(api(`/api/publico/cotizar/${encodeURIComponent(token)}/enviar`),
      { turnstileToken: turnstileToken ?? undefined, sitioWeb });
  }
}
