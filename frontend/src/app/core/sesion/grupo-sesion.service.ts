import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { DesafioMfa, SesionGrupo } from '../api/modelos';
import { api } from '../http/api';

const CLAVE_GRUPO = 'sc.grupo';

/**
 * Sesión del super administrador de un grupo de empresas (spec 035): contraseña + código MFA, con un JWT propio que
 * solo sirve en {@code /api/grupo/**}. Igual que la de la plataforma no tiene refresh; además se guarda en
 * {@code sessionStorage} para que seguir en la pestaña (volver desde una empresa) no obligue a ingresar de nuevo. Al
 * cerrar la pestaña la sesión termina.
 */
@Injectable({ providedIn: 'root' })
export class GrupoSesionService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sesion = signal<SesionGrupo | null>(this.leer());

  readonly nombre = computed(() => this.sesion()?.nombre ?? '');
  readonly grupoNombre = computed(() => this.sesion()?.grupoNombre ?? '');
  readonly autenticado = computed(() => this.sesion() !== null);

  /** JWT de grupo vigente, o {@code null}. */
  tokenActual(): string | null {
    return this.sesion()?.token ?? null;
  }

  /**
   * Primer paso: correo y contraseña. Devuelve el desafío para el código MFA (y el secreto si aún no se configuró).
   *
   * @param correo   correo del super administrador
   * @param password contraseña
   */
  ingresar(correo: string, password: string): Observable<DesafioMfa> {
    return this.http.post<DesafioMfa>(api('/api/grupo/auth/login'), { correo: correo.trim(), password });
  }

  /**
   * Segundo paso: el código de la app autenticadora.
   *
   * @param desafio desafío recibido en el primer paso
   * @param codigo  código de 6 dígitos
   */
  confirmar(desafio: string, codigo: string): Observable<SesionGrupo> {
    return this.http.post<SesionGrupo>(api('/api/grupo/auth/mfa'), { desafio, codigo: codigo.trim() })
      .pipe(tap(s => this.fijar(s)));
  }

  /** Cierra la sesión del grupo y vuelve a su ingreso. */
  salir(): void {
    this.sesion.set(null);
    try {
      sessionStorage.removeItem(CLAVE_GRUPO);
    } catch {
      // almacenamiento bloqueado: no hay nada que borrar
    }
    void this.router.navigateByUrl('/grupo/ingreso');
  }

  private fijar(s: SesionGrupo): void {
    this.sesion.set(s);
    try {
      sessionStorage.setItem(CLAVE_GRUPO, JSON.stringify(s));
    } catch {
      // modo privado: la sesión sigue en memoria
    }
  }

  private leer(): SesionGrupo | null {
    try {
      const texto = sessionStorage.getItem(CLAVE_GRUPO);
      return texto ? (JSON.parse(texto) as SesionGrupo) : null;
    } catch {
      return null;
    }
  }
}
