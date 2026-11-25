import { AfterViewInit, Component, ElementRef, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { MedioPago, Turno } from '../../../core/api/modelos';
import { CajaService } from '../../../core/servicios/caja.service';
import { Avisos } from '../../../core/ui/avisos';
import { Cifra } from '../../../core/ui/cifra';
import { MEDIOS } from '../caja';

/**
 * Cierre ciego del turno (spec 003): el cajero escribe lo que contó por medio sin ver lo esperado; el esperado y la
 * diferencia los devuelve el servidor al guardar y los muestra "Mi turno".
 */
@Component({
  selector: 'sc-cierre-turno',
  imports: [Cifra, FormsModule],
  template: `
    <section class="tarjeta">
      <h2>Cierre ciego</h2>
      <p class="sub">Cuente el efectivo y los comprobantes y escriba lo que hay. Lo que debería haber aparece después de guardar.</p>
      <div class="formulario">
        @for (m of medios; track m) {
          <div class="campo"><label [for]="'cc-' + m">{{ textoMedio(m) }}</label>
            <input scCifra [id]="'cc-' + m" min="0" class="entrada" [ngModel]="contado()[m] ?? null" (ngModelChange)="contar(m, $event)"></div>
        }
        <div class="campo ancho"><label for="cc-obs">Observación</label>
          <input id="cc-obs" class="entrada" maxlength="500" placeholder="Opcional" [ngModel]="observacion()" (ngModelChange)="observacion.set($event)"></div>
      </div>
      <button type="button" class="boton primario" [disabled]="contado()['EFECTIVO'] == null || enviando()" (click)="cerrar()">Confirmar cierre</button>
    </section>
  `,
  styles: `
    h2 { margin: 0 0 8px; }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .formulario { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 12px; .ancho { grid-column: 1 / -1; } }
  `,
})
export class CierreTurno implements AfterViewInit {
  private readonly caja = inject(CajaService);
  private readonly avisos = inject(Avisos);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Turno abierto que se cierra. */
  readonly turnoId = input.required<number>();
  /** El turno cerrado, con lo esperado y las diferencias. */
  readonly cerrado = output<Turno>();

  protected readonly medios = Object.keys(MEDIOS) as MedioPago[];
  protected readonly contado = signal<Partial<Record<MedioPago, number>>>({});
  protected readonly observacion = signal('');
  protected readonly enviando = signal(false);

  /** Al abrirse, el foco va al efectivo contado. */
  ngAfterViewInit(): void {
    this.elemento.nativeElement.querySelector<HTMLInputElement>('#cc-EFECTIVO')?.focus();
  }

  /** @param m medio; @param valor contado */
  protected contar(m: MedioPago, valor: number | null): void {
    this.contado.update(c => ({ ...c, [m]: valor ?? undefined }));
  }

  protected cerrar(): void {
    const c = this.contado();
    const otros: Partial<Record<MedioPago, number>> = {};
    this.medios.filter(m => m !== 'EFECTIVO').forEach(m => (otros[m] = c[m] ?? 0));
    this.enviando.set(true);
    this.caja.cerrar(this.turnoId(), c['EFECTIVO'] ?? 0, otros, this.observacion().trim() || null).subscribe({
      next: t => {
        this.enviando.set(false);
        this.cerrado.emit(t);
      },
      error: e => {
        this.enviando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param m medio @returns texto */
  protected textoMedio(m: MedioPago): string {
    return MEDIOS[m];
  }
}
