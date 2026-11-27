import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { EmpresaEntrable, TableroGrupo } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { PesosPipe } from '../../core/formato/formato';
import { mensajeError } from '../../core/http/api';
import { GrupoService } from '../../core/servicios/grupo.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Zona del grupo (spec 035): las empresas con su botón "Entrar" y el tablero del grupo con filtro de fechas. Todas las
 * cifras y el total vienen calculados del backend; esta pantalla solo las dibuja.
 */
@Component({
  selector: 'sc-grupo-inicio',
  imports: [FormsModule, Estado, PesosPipe],
  template: `
    <div class="pagina">
      <div><h1>Tus empresas</h1>
        <p class="texto-2">Entra a una empresa para trabajar en ella. Dentro verás el botón "Cambiar de empresa" para volver aquí.</p></div>

      <section class="tarjeta">
        <h2>Empresas del grupo</h2>
        @if (error()) { <div class="aviso rojo" role="alert">{{ error() }}</div> }
        <div class="empresas">
          @for (e of empresas(); track e.id) {
            <div class="empresa">
              <div><strong>{{ e.razonSocial }}</strong></div>
              <sc-estado [valor]="e.estado" />
              <button type="button" class="boton primario chico" [disabled]="entrando() !== null || e.estado === 'SUSPENDIDA'"
                      [title]="e.estado === 'SUSPENDIDA' ? 'La suscripción está suspendida' : ''" (click)="entrar(e)">
                {{ entrando() === e.id ? 'Entrando…' : 'Entrar' }}
              </button>
            </div>
          } @empty {
            <p class="vacio">{{ empresas() ? 'Tu grupo todavía no tiene empresas.' : 'Cargando…' }}</p>
          }
        </div>
      </section>

      <section class="tarjeta">
        <h2>Tablero del grupo</h2>
        <form class="filtros" (ngSubmit)="cargarTablero()">
          <div class="campo"><label for="gd">Desde</label>
            <input id="gd" name="desde" type="date" class="entrada" [(ngModel)]="desde"></div>
          <div class="campo"><label for="gh">Hasta</label>
            <input id="gh" name="hasta" type="date" class="entrada" [(ngModel)]="hasta"></div>
          <button type="submit" class="boton primario" [disabled]="cargando()">Consultar</button>
        </form>
        @if (tablero(); as t) {
          <p class="texto-chico texto-2">Periodo del {{ t.desde }} al {{ t.hasta }}. Ventas netas = ventas menos devoluciones; compras con impuestos; inventario a costo.</p>
          <div class="tabla-contenedor">
            <table class="tabla">
              <thead><tr><th>Empresa</th><th class="num">Ventas netas</th><th class="num">Compras</th><th class="num">Inventario valorizado</th></tr></thead>
              <tbody>
                @for (f of t.empresas ?? []; track f.empresaId) {
                  <tr><td>{{ f.razonSocial }}</td><td class="num">{{ f.ventasNetas | pesos }}</td>
                    <td class="num">{{ f.compras | pesos }}</td><td class="num">{{ f.inventarioValorizado | pesos }}</td></tr>
                } @empty {
                  <tr><td colspan="4" class="vacio">Sin empresas en el grupo.</td></tr>
                }
              </tbody>
              @if (t.total; as g) {
                <tfoot><tr class="total"><td>Total del grupo</td><td class="num">{{ g.ventasNetas | pesos }}</td>
                  <td class="num">{{ g.compras | pesos }}</td><td class="num">{{ g.inventarioValorizado | pesos }}</td></tr></tfoot>
              }
            </table>
          </div>
        } @else if (errorTablero()) {
          <div class="aviso rojo" role="alert">{{ errorTablero() }}</div>
        } @else {
          <p class="vacio">Cargando…</p>
        }
      </section>
    </div>
  `,
  styles: `
    .pagina { display: flex; flex-direction: column; gap: 20px; }
    h1, h2, p { margin: 0 0 8px; }
    .empresas { display: flex; flex-direction: column; }
    .empresa { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; padding: 12px 0; border-top: 1px solid #f0f0f0;
      > div { flex: 1 1 200px; min-width: 0; } }
    .filtros { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 12px; margin-bottom: 12px; }
    tfoot .total td { font-weight: 700; border-top: 2px solid #ddd; }
  `,
})
export class GrupoInicio implements OnInit {
  private readonly servicio = inject(GrupoService);
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly avisos = inject(Avisos);

  protected readonly empresas = signal<EmpresaEntrable[] | null>(null);
  protected readonly tablero = signal<TableroGrupo | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly errorTablero = signal<string | null>(null);
  protected readonly cargando = signal(false);
  /** Empresa a la que se está entrando (bloquea los demás botones). */
  protected readonly entrando = signal<number | null>(null);
  protected desde = '';
  protected hasta = '';

  ngOnInit(): void {
    this.servicio.empresas().subscribe({
      next: l => this.empresas.set(l),
      error: e => {
        this.empresas.set([]);
        this.error.set(mensajeError(e));
      },
    });
    this.cargarTablero();
  }

  /** Pide el tablero del periodo escrito (vacío = el mes en curso, lo decide el backend). */
  protected cargarTablero(): void {
    this.cargando.set(true);
    this.errorTablero.set(null);
    this.servicio.tablero(this.desde || null, this.hasta || null).subscribe({
      next: t => {
        this.tablero.set(t);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.errorTablero.set(mensajeError(e));
      },
    });
  }

  /**
   * Entra a la empresa con la sesión normal que entrega el backend y abre su inicio.
   *
   * @param e empresa del grupo
   */
  protected entrar(e: EmpresaEntrable): void {
    this.entrando.set(e.id!);
    this.servicio.entrar(e.id!).subscribe({
      next: r => this.sesion.iniciarDesdeGrupo(r).subscribe({
        next: () => void this.router.navigateByUrl('/app/inicio'),
        error: err => this.falloEntrar(err),
      }),
      error: err => this.falloEntrar(err),
    });
  }

  private falloEntrar(e: unknown): void {
    this.entrando.set(null);
    this.avisos.error(e);
  }
}
