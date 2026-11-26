import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AccionAutorizada, Autorizacion, Caja, CajaRequest, CategoriaMovimientoCaja, ConfiguracionCaja, ConteoDenominacion, EsperadoTurno, EstadoTurno, MedioPago,
  Pagina, TipoCategoriaMovimiento, Turno, UsuarioCaja,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Cajas, turnos y autorizaciones con PIN (spec 003). Esperados y diferencias los calcula el backend. */
@Injectable({ providedIn: 'root' })
export class CajaService {
  private readonly http = inject(HttpClient);

  /** @param activa filtro por estado (null = todas) */
  cajas(activa: boolean | null = null): Observable<Caja[]> {
    return this.http.get<Caja[]>(api('/api/cajas'), { params: parametros({ activa }) });
  }

  /** Spec 025: cajas activas en las que la sesión puede abrir turno (las asignadas o, sin ninguna, todas). */
  misCajas(): Observable<Caja[]> {
    return this.http.get<Caja[]>(api('/api/cajas/mias'));
  }

  /** @param cajaId caja @returns quiénes la pueden usar (spec 025) */
  usuariosDeCaja(cajaId: number): Observable<UsuarioCaja[]> {
    return this.http.get<UsuarioCaja[]>(api(`/api/cajas/${cajaId}/usuarios`));
  }

  /** @param usuarioId persona @returns sus cajas asignadas (vacío = puede usar todas) */
  cajasDeUsuario(usuarioId: number): Observable<{ cajaId?: number; nombre?: string }[]> {
    return this.http.get<{ cajaId?: number; nombre?: string }[]>(api(`/api/cajas/de-usuario/${usuarioId}`));
  }

  /** @param cajaId caja; @param usuarioIds personas que la pueden usar (reemplaza la lista) */
  asignarUsuarios(cajaId: number, usuarioIds: number[]): Observable<UsuarioCaja[]> {
    return this.http.put<UsuarioCaja[]>(api(`/api/cajas/${cajaId}/usuarios`), { usuarioIds });
  }

  /** @param datos caja nueva */
  crearCaja(datos: CajaRequest): Observable<Caja> {
    return this.http.post<Caja>(api('/api/cajas'), datos);
  }

  /** @param id caja; @param datos datos nuevos */
  editarCaja(id: number, datos: CajaRequest): Observable<Caja> {
    return this.http.put<Caja>(api(`/api/cajas/${id}`), datos);
  }

  configuracion(): Observable<ConfiguracionCaja> {
    return this.http.get<ConfiguracionCaja>(api('/api/cajas/configuracion'));
  }

  /**
   * @param toleranciaDiferencia diferencia aceptada sin aprobación
   * @param bodegaAveriasId      bodega para lo devuelto en mal estado (null = no reingresa)
   * @param umbrales             anulaciones y descuentos por cajero y día que avisan al dueño (spec 010; vacío = no cambian)
   */
  configurar(toleranciaDiferencia: number, bodegaAveriasId: number | null = null,
    umbrales: { anulaciones: number | null; descuentos: number | null } | null = null): Observable<ConfiguracionCaja> {
    return this.http.put<ConfiguracionCaja>(api('/api/cajas/configuracion'), {
      toleranciaDiferencia, bodegaAveriasId,
      umbralAnulacionesDia: umbrales?.anulaciones ?? null, umbralDescuentosDia: umbrales?.descuentos ?? null,
    });
  }

  /** @param filtros estado, solo pendientes de aprobación, caja y página */
  turnos(filtros: { estado?: EstadoTurno | null; pendientes?: boolean; cajaId?: number | null; pagina?: number }): Observable<Pagina<Turno>> {
    return this.http.get<Pagina<Turno>>(api('/api/turnos'), { params: parametros({
      estado: filtros.estado, pendientes: filtros.pendientes ?? false, cajaId: filtros.cajaId, page: filtros.pagina ?? 0, size: 20 }) });
  }

  /** @param id turno; detalle con el libro de caja (tiquete Z si está cerrado) */
  turno(id: number): Observable<Turno> {
    return this.http.get<Turno>(api(`/api/turnos/${id}`));
  }

  /** @param id turno; @param explicacion qué pasó con la diferencia */
  aprobar(id: number, explicacion: string): Observable<Turno> {
    return this.http.post<Turno>(api(`/api/turnos/${id}/aprobar`), { explicacion });
  }

  /** Turno abierto de la sesión (404 si no tiene). */
  actual(): Observable<Turno> {
    return this.http.get<Turno>(api('/api/turnos/actual'));
  }

  /** @param cajaId caja; @param baseApertura efectivo contado al abrir */
  abrir(cajaId: number, baseApertura: number): Observable<Turno> {
    return this.http.post<Turno>(api('/api/turnos'), { cajaId, baseApertura });
  }

  /**
   * Retiro, gasto o ingreso con autorización.
   *
   * @param turnoId turno
   * @param datos   tipo, monto, motivo, soporte, token de autorización y categoría (spec 021)
   */
  movimiento(turnoId: number, datos: { tipo: 'RETIRO' | 'GASTO' | 'INGRESO'; monto: number; motivo: string; soporte?: string | null;
    autorizacion?: string; categoriaId?: number | null }): Observable<Turno> {
    return this.http.post<Turno>(api(`/api/turnos/${turnoId}/movimientos`), datos);
  }

  /**
   * Cierre ciego.
   *
   * @param turnoId         turno
   * @param contadoEfectivo efectivo contado
   * @param contadoOtros    vouchers y transferencias por medio
   * @param observacion     nota opcional
   * @param conteo          billetes y monedas contados (spec 023); si viene, el backend suma el efectivo
   */
  cerrar(turnoId: number, contadoEfectivo: number | null, contadoOtros: Partial<Record<MedioPago, number>>, observacion: string | null,
    conteo: ConteoDenominacion[] | null = null): Observable<Turno> {
    return this.http.post<Turno>(api(`/api/turnos/${turnoId}/cerrar`), { contadoEfectivo, contadoOtros, observacion, conteo });
  }

  /**
   * El supervisor escribe correo y PIN en la pantalla del cajero; devuelve un permiso de un solo uso.
   *
   * @param datos correo, PIN, acción, turno y referencia
   */
  autorizar(datos: { correo: string; pin: string; accion: AccionAutorizada; turnoId: number; referenciaId?: number | null }): Observable<Autorizacion> {
    return this.http.post<Autorizacion>(api('/api/autorizaciones'), datos);
  }

  /**
   * Lo que debería haber en un turno (spec 021, D20): solo para quien supervisa; el cajero cierra a ciegas.
   *
   * @param turnoId turno
   */
  esperado(turnoId: number): Observable<EsperadoTurno> {
    return this.http.get<EsperadoTurno>(api(`/api/turnos/${turnoId}/esperado`));
  }

  /**
   * Categorías de ingresos y egresos de caja (spec 021).
   *
   * @param tipo   filtro por tipo (null = los dos)
   * @param activa filtro por estado (null = todas)
   */
  categorias(tipo: TipoCategoriaMovimiento | null = null, activa: boolean | null = null): Observable<CategoriaMovimientoCaja[]> {
    return this.http.get<CategoriaMovimientoCaja[]>(api('/api/cajas/categorias-movimiento'), { params: parametros({ tipo, activa }) });
  }

  /** @param tipo ingreso o egreso; @param nombre nombre visible */
  crearCategoria(tipo: TipoCategoriaMovimiento, nombre: string): Observable<CategoriaMovimientoCaja> {
    return this.http.post<CategoriaMovimientoCaja>(api('/api/cajas/categorias-movimiento'), { tipo, nombre });
  }

  /** @param id categoría; @param nombre nombre nuevo; @param activa si se puede elegir */
  editarCategoria(id: number, nombre: string, activa: boolean): Observable<CategoriaMovimientoCaja> {
    return this.http.put<CategoriaMovimientoCaja>(api(`/api/cajas/categorias-movimiento/${id}`), { nombre, activa });
  }

  /** Si la sesión ya fijó su PIN de supervisor. */
  tienePin(): Observable<{ tienePin: boolean }> {
    return this.http.get<{ tienePin: boolean }>(api('/api/autorizaciones/mi-pin'));
  }

  /** @param passwordActual contraseña de la cuenta; @param pin PIN nuevo de 6 dígitos */
  cambiarPin(passwordActual: string, pin: string): Observable<void> {
    return this.http.put<void>(api('/api/autorizaciones/mi-pin'), { passwordActual, pin });
  }
}
