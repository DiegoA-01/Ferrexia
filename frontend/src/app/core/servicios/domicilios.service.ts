import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Domiciliario, Domicilio, EnvioDomicilio, EstadoDomicilio, PagoVentaRequest } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Domicilios (spec 032): tablero, estados, cobro de la contraentrega y envío. */
@Injectable({ providedIn: 'root' })
export class DomiciliosService {
  private readonly http = inject(HttpClient);

  /** @param fecha día ISO (null = hoy); @param estado estado (null = todos) */
  listar(fecha: string | null, estado: EstadoDomicilio | null): Observable<Domicilio[]> {
    return this.http.get<Domicilio[]>(api('/api/domicilios'), { params: parametros({ fecha, estado }) });
  }

  /** @param id domicilio; con su historial */
  obtener(id: number): Observable<Domicilio> {
    return this.http.get<Domicilio>(api(`/api/domicilios/${id}`));
  }

  /** @param id domicilio; @param datos domiciliario interno (id) o externo (nombre y teléfono) */
  asignar(id: number, datos: { domiciliarioUsuarioId?: number; nombre?: string; telefono?: string }): Observable<Domicilio> {
    return this.http.post<Domicilio>(api(`/api/domicilios/${id}/asignar`), datos);
  }

  /** @param id domicilio; @param estado estado nuevo; @param nota nota (obligatoria para devolver o cancelar) */
  cambiarEstado(id: number, estado: EstadoDomicilio, nota: string | null): Observable<Domicilio> {
    return this.http.post<Domicilio>(api(`/api/domicilios/${id}/estado`), { estado, nota: nota || undefined });
  }

  /** @param id domicilio contraentrega entregado; @param pagos pagos que suman el total */
  cobrar(id: number, pagos: PagoVentaRequest[]): Observable<Domicilio> {
    return this.http.post<Domicilio>(api(`/api/domicilios/${id}/cobrar`), { pagos });
  }

  /** Producto del envío y su precio (vacío si no está configurado). */
  envio(): Observable<EnvioDomicilio> {
    return this.http.get<EnvioDomicilio>(api('/api/domicilios/envio'));
  }

  /** @param precio precio del envío en la lista General */
  configurarEnvio(precio: number): Observable<EnvioDomicilio> {
    return this.http.put<EnvioDomicilio>(api('/api/domicilios/envio'), { precio });
  }

  /** Usuarios con el permiso de repartir domicilios. */
  domiciliarios(): Observable<Domiciliario[]> {
    return this.http.get<Domiciliario[]>(api('/api/domicilios/domiciliarios'));
  }
}
