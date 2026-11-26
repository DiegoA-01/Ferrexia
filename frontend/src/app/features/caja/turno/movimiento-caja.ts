import { AfterViewInit, Component, ElementRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AccionAutorizada, CategoriaMovimientoCaja, Turno } from '../../../core/api/modelos';
import { CajaService } from '../../../core/servicios/caja.service';
import { SesionService } from '../../../core/sesion/sesion.service';
import { Avisos } from '../../../core/ui/avisos';
import { Cifra } from '../../../core/ui/cifra';
import { AutorizacionPin } from '../autorizacion-pin';

/** Movimiento de efectivo que registra el cajero. */
export type TipoMovimiento = 'RETIRO' | 'GASTO' | 'INGRESO';

/** Permiso que deja registrar cada movimiento sin el PIN del supervisor (spec 025, D15). */
export const PERMISO_SIN_PIN: Record<TipoMovimiento, string> = {
  RETIRO: 'CAJA_RETIRO_SIN_PIN',
  GASTO: 'CAJA_GASTO_SIN_PIN',
  INGRESO: 'CAJA_INGRESO_SIN_PIN',
};

/** Texto de cada movimiento. */
export const TIPOS_MOVIMIENTO: Record<TipoMovimiento, string> = {
  RETIRO: 'Retiro a la bóveda',
  GASTO: 'Gasto menor',
  INGRESO: 'Ingreso de efectivo',
};

/**
 * Formulario de un retiro, gasto o ingreso de efectivo del turno (spec 003). El tipo lo elige "Mi turno" (spec 018) y
 * el movimiento se registra con la autorización de un solo uso del supervisor. El gasto y el ingreso llevan una categoría
 * de su tipo; el retiro puede llevarla (spec 021). Las categorías las crea el administrador en Cajas y turnos.
 */
@Component({
  selector: 'sc-movimiento-caja',
  imports: [Cifra, FormsModule, AutorizacionPin],
  template: `
    <section class="tarjeta">
      <h2>{{ titulo() }}</h2>
      <div class="formulario">
        <div class="campo"><label for="mt-monto">Monto</label>
          <input scCifra id="mt-monto" min="0" class="entrada" [ngModel]="monto()" (ngModelChange)="monto.set($event)"></div>
        <div class="campo"><label for="mt-categoria">Categoría{{ obligatoria() ? '' : ' (opcional)' }}</label>
          <select id="mt-categoria" class="entrada" [ngModel]="categoriaId()" (ngModelChange)="categoriaId.set($event)">
            <option [ngValue]="null">{{ obligatoria() ? 'Elija la categoría' : 'Sin categoría' }}</option>
            @for (c of categorias(); track c.id) { <option [ngValue]="c.id">{{ c.nombre }}</option> }
          </select>
          @if (obligatoria() && cargadas() && !categorias().length) {
            <span class="sub">No hay categorías activas: pida al administrador que las cree en Cajas y turnos.</span>
          }</div>
        <div class="campo ancho"><label for="mt-motivo">Motivo</label>
          <input id="mt-motivo" class="entrada" maxlength="300" [ngModel]="motivo()" (ngModelChange)="motivo.set($event)"></div>
        <div class="campo"><label for="mt-soporte">Soporte (recibo)</label>
          <input id="mt-soporte" class="entrada" maxlength="100" placeholder="Opcional" [ngModel]="soporte()" (ngModelChange)="soporte.set($event)"></div>
      </div>
      @if (sinPin()) {
        <button type="button" class="boton" [disabled]="!valido() || enviando()" (click)="registrar(null)">Registrar</button>
        <span class="sub">Tiene permiso para registrarlo sin el PIN del supervisor.</span>
      } @else if (pidiendo()) {
        <sc-autorizacion-pin [accion]="accion()" [turnoId]="turnoId()" (autorizado)="registrar($event.token)" (cancelado)="pidiendo.set(false)" />
      } @else {
        <button type="button" class="boton" [disabled]="!valido()" (click)="pidiendo.set(true)">Registrar (pide PIN)</button>
      }
    </section>
  `,
  styles: `
    h2 { margin: 0 0 8px; }
    .formulario { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 12px; .ancho { grid-column: 1 / -1; } }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
  `,
})
export class MovimientoCaja implements AfterViewInit {
  private readonly caja = inject(CajaService);
  private readonly avisos = inject(Avisos);
  private readonly sesion = inject(SesionService);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Turno abierto al que se carga el movimiento. */
  readonly turnoId = input.required<number>();
  /** Retiro, gasto o ingreso. */
  readonly tipo = input.required<TipoMovimiento>();
  /** El turno con el movimiento ya registrado. */
  readonly actualizado = output<Turno>();

  protected readonly monto = signal<number | null>(null);
  protected readonly motivo = signal('');
  protected readonly soporte = signal('');
  protected readonly pidiendo = signal(false);
  /** Categorías activas del tipo que toca (egreso para retiro y gasto, ingreso para ingreso). */
  protected readonly categorias = signal<CategoriaMovimientoCaja[]>([]);
  protected readonly cargadas = signal(false);
  protected readonly categoriaId = signal<number | null>(null);
  /** El gasto y el ingreso exigen categoría; el retiro no. */
  protected readonly obligatoria = computed(() => this.tipo() !== 'RETIRO');

  constructor() {
    // Cada vez que cambia el tipo se cargan sus categorías y se borra la elegida.
    effect(() => {
      const tipo = this.tipo();
      untracked(() => this.cargarCategorias(tipo));
    });
  }

  /** Spec 025 (D15): con el permiso del tipo, el movimiento no pide el PIN del supervisor. */
  protected readonly sinPin = computed(() => this.sesion.tienePermiso(PERMISO_SIN_PIN[this.tipo()]));
  protected readonly enviando = signal(false);

  protected readonly titulo = computed(() => `Registrar ${TIPOS_MOVIMIENTO[this.tipo()].toLowerCase()}`);
  protected readonly accion = computed<AccionAutorizada>(() => this.tipo());
  protected readonly valido = computed(() => (this.monto() ?? 0) > 0 && this.motivo().trim().length > 2
    && (!this.obligatoria() || this.categoriaId() != null));

  /** Al abrirse, el foco va al monto. */
  ngAfterViewInit(): void {
    this.elemento.nativeElement.querySelector<HTMLInputElement>('#mt-monto')?.focus();
  }

  /** @param token permiso de un solo uso del supervisor ({@code null} si la persona registra sin PIN) */
  protected registrar(token: string | null): void {
    this.pidiendo.set(false);
    this.enviando.set(true);
    this.caja.movimiento(this.turnoId(), {
      tipo: this.tipo(), monto: this.monto()!, motivo: this.motivo().trim(), soporte: this.soporte().trim() || null,
      autorizacion: token ?? undefined, categoriaId: this.categoriaId(),
    }).subscribe({
      next: nuevo => {
        this.enviando.set(false);
        this.monto.set(null);
        this.motivo.set('');
        this.soporte.set('');
        this.categoriaId.set(null);
        this.avisos.ok('Movimiento registrado.');
        this.actualizado.emit(nuevo);
      },
      error: e => {
        this.enviando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param tipo movimiento; carga las categorías activas de su tipo */
  private cargarCategorias(tipo: TipoMovimiento): void {
    this.categoriaId.set(null);
    this.cargadas.set(false);
    this.caja.categorias(tipo === 'INGRESO' ? 'INGRESO' : 'EGRESO', true).subscribe({
      next: l => {
        this.categorias.set(l);
        this.cargadas.set(true);
      },
      error: e => this.avisos.error(e),
    });
  }
}
