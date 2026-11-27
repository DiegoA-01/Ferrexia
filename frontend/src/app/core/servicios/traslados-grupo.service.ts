import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  DestinoTraslado, Pagina, TrasladoDetalle, TrasladoEnvio, TrasladoRecepcion, TrasladoResumen,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Traslados de mercancía entre empresas del mismo grupo (spec 035). Va con la sesión de la empresa. */
@Injectable({ providedIn: 'root' })
export class TrasladosGrupoService {
  private readonly http = inject(HttpClient);

  /** Empresas del mismo grupo a las que se puede enviar. */
  destinos(): Observable<DestinoTraslado[]> {
    return this.http.get<DestinoTraslado[]>(api('/api/traslados-grupo/destinos'));
  }

  /** @param pagina número de página desde 0; enviados y recibidos de la empresa */
  listar(pagina: number): Observable<Pagina<TrasladoResumen>> {
    return this.http.get<Pagina<TrasladoResumen>>(api('/api/traslados-grupo'), { params: parametros({ page: pagina, size: 20 }) });
  }

  /** @param id traslado */
  obtener(id: number): Observable<TrasladoDetalle> {
    return this.http.get<TrasladoDetalle>(api(`/api/traslados-grupo/${id}`));
  }

  /** @param datos destino, bodega de salida, líneas y nota */
  enviar(datos: TrasladoEnvio): Observable<TrasladoDetalle> {
    return this.http.post<TrasladoDetalle>(api('/api/traslados-grupo'), datos);
  }

  /**
   * @param id    traslado en tránsito, desde la empresa destino
   * @param datos bodega de entrada y lo recibido por línea
   */
  recibir(id: number, datos: TrasladoRecepcion): Observable<TrasladoDetalle> {
    return this.http.post<TrasladoDetalle>(api(`/api/traslados-grupo/${id}/recibir`), datos);
  }

  /**
   * @param id     traslado en tránsito, desde la empresa origen
   * @param motivo motivo de la anulación
   */
  anular(id: number, motivo: string): Observable<TrasladoDetalle> {
    return this.http.post<TrasladoDetalle>(api(`/api/traslados-grupo/${id}/anular`), { motivo });
  }
}
