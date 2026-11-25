import { AfterViewInit, Component, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AccionAutorizada, Turno } from '../../../core/api/modelos';
import { CajaService } from '../../../core/servicios/caja.service';
import { Avisos } from '../../../core/ui/avisos';
import { Cifra } from '../../../core/ui/cifra';
import { AutorizacionPin } from '../autorizacion-pin';

/** Movimiento de efectivo que registra el cajero. */
export type TipoMovimiento = 'RETIRO' | 'GASTO' | 'INGRESO';

/** Texto de cada movimiento. */
export const TIPOS_MOVIMIENTO: Record<TipoMovimiento, string> = {
  RETIRO: 'Retiro a la bóveda',
  GASTO: 'Gasto menor',
  INGRESO: 'Ingreso de efectivo',
};

/**
 * Formulario de un retiro, gasto o ingreso de efectivo del turno (spec 003). El tipo lo elige "Mi turno" (spec 018) y
 * el movimiento se registra con la autorización de un solo uso del supervisor.
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
        <div class="campo ancho"><label for="mt-motivo">Motivo</label>
          <input id="mt-motivo" class="entrada" maxlength="300" [ngModel]="motivo()" (ngModelChange)="motivo.set($event)"></div>
        <div class="campo"><label for="mt-soporte">Soporte (recibo)</label>
          <input id="mt-soporte" class="entrada" maxlength="100" placeholder="Opcional" [ngModel]="soporte()" (ngModelChange)="soporte.set($event)"></div>
      </div>
      @if (pidiendo()) {
        <sc-autorizacion-pin [accion]="accion()" [turnoId]="turnoId()" (autorizado)="registrar($event.token)" (cancelado)="pidiendo.set(false)" />
      } @else {
        <button type="button" class="boton" [disabled]="!valido()" (click)="pidiendo.set(true)">Registrar (pide PIN)</button>
      }
    </section>
  `,
  styles: `
    h2 { margin: 0 0 8px; }
    .formulario { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 12px; .ancho { grid-column: 1 / -1; } }
  `,
})
export class MovimientoCaja implements AfterViewInit {
  private readonly caja = inject(CajaService);
  private readonly avisos = inject(Avisos);
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

  protected readonly titulo = computed(() => `Registrar ${TIPOS_MOVIMIENTO[this.tipo()].toLowerCase()}`);
  protected readonly accion = computed<AccionAutorizada>(() => this.tipo());
  protected readonly valido = computed(() => (this.monto() ?? 0) > 0 && this.motivo().trim().length > 2);

  /** Al abrirse, el foco va al monto. */
  ngAfterViewInit(): void {
    this.elemento.nativeElement.querySelector<HTMLInputElement>('#mt-monto')?.focus();
  }

  /** @param token permiso de un solo uso del supervisor */
  protected registrar(token: string): void {
    this.pidiendo.set(false);
    this.caja.movimiento(this.turnoId(), {
      tipo: this.tipo(), monto: this.monto()!, motivo: this.motivo().trim(), soporte: this.soporte().trim() || null, autorizacion: token,
    }).subscribe({
      next: nuevo => {
        this.monto.set(null);
        this.motivo.set('');
        this.soporte.set('');
        this.avisos.ok('Movimiento registrado.');
        this.actualizado.emit(nuevo);
      },
      error: e => this.avisos.error(e),
    });
  }
}
