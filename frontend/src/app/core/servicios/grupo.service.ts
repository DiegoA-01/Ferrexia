import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { EmpresaEntrable, LoginResponse, TableroGrupo } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Zona del super administrador de un grupo (spec 035): sus empresas, entrar a una y el tablero del grupo. */
@Injectable({ providedIn: 'root' })
export class GrupoService {
  private readonly http = inject(HttpClient);

  /** Empresas del grupo a las que se puede entrar. */
  empresas(): Observable<EmpresaEntrable[]> {
    return this.http.get<EmpresaEntrable[]>(api('/api/grupo/empresas'));
  }

  /**
   * Entra a una empresa del grupo sin pedir la clave: devuelve la sesión normal de esa empresa.
   *
   * @param empresaId empresa del grupo
   */
  entrar(empresaId: number): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(api(`/api/grupo/entrar/${empresaId}`), {});
  }

  /**
   * Ventas, compras e inventario por empresa y en total (todo calculado por el backend).
   *
   * @param desde primer día {@code AAAA-MM-DD} (vacío = primer día del mes)
   * @param hasta último día (vacío = hoy)
   */
  tablero(desde: string | null, hasta: string | null): Observable<TableroGrupo> {
    return this.http.get<TableroGrupo>(api('/api/grupo/tablero'), { params: parametros({ desde, hasta }) });
  }
}
