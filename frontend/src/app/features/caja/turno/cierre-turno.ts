import { AfterViewInit, Component, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ConteoDenominacion, MedioPago, TipoDenominacion, Turno } from '../../../core/api/modelos';
import { PesosPipe } from '../../../core/formato/formato';
import { CajaService } from '../../../core/servicios/caja.service';
import { Avisos } from '../../../core/ui/avisos';
import { Cifra } from '../../../core/ui/cifra';
import { MEDIOS } from '../caja';

/** Una fila del conteo del efectivo. */
interface Denominacion {
  readonly tipo: TipoDenominacion;
  readonly valor: number;
}

/** Billetes colombianos vigentes, de mayor a menor (spec 023). Los mismos que acepta el backend. */
export const BILLETES: readonly Denominacion[] = [100_000, 50_000, 20_000, 10_000, 5_000, 2_000, 1_000]
  .map(valor => ({ tipo: 'BILLETE' as const, valor }));
/** Monedas colombianas vigentes, de mayor a menor. */
export const MONEDAS: readonly Denominacion[] = [1_000, 500, 200, 100, 50].map(valor => ({ tipo: 'MONEDA' as const, valor }));

/** @param d denominación @returns clave única de la fila (el $1.000 está como billete y como moneda) */
const clave = (d: Denominacion): string => `${d.tipo}-${d.valor}`;

/**
 * Cierre ciego del turno (spec 003). El efectivo se cuenta por billete y moneda (spec 023): el cajero escribe cuántos
 * tiene de cada uno y la pantalla muestra el subtotal y el total; el backend vuelve a sumar y guarda el conteo. Los otros
 * medios se escriben como antes. Lo esperado y la diferencia los devuelve el servidor al guardar.
 */
@Component({
  selector: 'sc-cierre-turno',
  imports: [Cifra, FormsModule, PesosPipe],
  template: `
    <section class="tarjeta">
      <h2>Cierre ciego</h2>
      <p class="sub">Cuente el efectivo por billete y moneda, y escriba los comprobantes. Lo que debería haber aparece después de guardar.</p>

      <div class="conteo">
        @for (grupo of grupos; track grupo.titulo) {
          <table class="tabla">
            <caption>{{ grupo.titulo }}</caption>
            <thead><tr><th>Valor</th><th class="num">Cantidad</th><th class="num">Subtotal</th><th>Referencia</th></tr></thead>
            <tbody>
              @for (d of grupo.filas; track clave(d)) {
                <tr>
                  <td data-etiqueta="Valor"><label [for]="'cc-' + clave(d)">{{ d.valor | pesos }}</label></td>
                  <td data-etiqueta="Cantidad" class="num">
                    <input scCifra [decimales]="0" [id]="'cc-' + clave(d)" class="entrada cantidad" placeholder="0"
                      [attr.aria-label]="'Cantidad de ' + (d.tipo === 'BILLETE' ? 'billetes' : 'monedas') + ' de ' + d.valor"
                      [ngModel]="cantidades()[clave(d)] ?? null" (ngModelChange)="contarDenominacion(d, $event)"></td>
                  <td data-etiqueta="Subtotal" class="num">{{ subtotal(d) | pesos }}</td>
                  <td data-etiqueta="Referencia">
                    <input class="entrada" maxlength="100" placeholder="Opcional" [attr.aria-label]="'Referencia de ' + d.valor"
                      [ngModel]="referencias()[clave(d)] ?? ''" (ngModelChange)="referir(d, $event)"></td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
      <p class="total">Efectivo contado: <strong data-prueba="total-efectivo">{{ totalEfectivo() | pesos }}</strong></p>

      <div class="formulario">
        @for (m of otrosMedios; track m) {
          <div class="campo"><label [for]="'cc-' + m">{{ textoMedio(m) }}</label>
            <input scCifra [id]="'cc-' + m" min="0" class="entrada" [ngModel]="contado()[m] ?? null" (ngModelChange)="contar(m, $event)"></div>
        }
        <div class="campo ancho"><label for="cc-obs">Nota general</label>
          <input id="cc-obs" class="entrada" maxlength="500" placeholder="Opcional" [ngModel]="observacion()" (ngModelChange)="observacion.set($event)"></div>
      </div>
      @if (!contoAlgo()) {
        <p class="aviso">Escriba la cantidad de al menos un billete o moneda (0 si no hay efectivo).</p>
      }
      <button type="button" class="boton primario" [disabled]="!contoAlgo() || enviando()" (click)="cerrar()">Confirmar cierre</button>
    </section>
  `,
  styles: `
    h2 { margin: 0 0 8px; }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .conteo { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; margin: 12px 0; }
    caption { text-align: left; font-weight: 600; padding-bottom: 4px; }
    .num { text-align: right; }
    .cantidad { max-width: 90px; text-align: right; }
    .total { font-size: 15px; margin: 0 0 12px; }
    .aviso { font-size: 12px; color: var(--sc-texto-2); margin: 0 0 8px; }
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

  protected readonly grupos = [{ titulo: 'Billetes', filas: BILLETES }, { titulo: 'Monedas', filas: MONEDAS }];
  protected readonly clave = clave;
  protected readonly otrosMedios = (Object.keys(MEDIOS) as MedioPago[]).filter(m => m !== 'EFECTIVO');
  protected readonly contado = signal<Partial<Record<MedioPago, number>>>({});
  /** Cantidad escrita por denominación (clave → unidades). */
  protected readonly cantidades = signal<Record<string, number | undefined>>({});
  protected readonly referencias = signal<Record<string, string>>({});
  protected readonly observacion = signal('');
  protected readonly enviando = signal(false);

  /** Total del efectivo según el conteo (en pantalla; el backend lo vuelve a calcular). */
  protected readonly totalEfectivo = computed(() =>
    [...BILLETES, ...MONEDAS].reduce((s, d) => s + d.valor * (this.cantidades()[clave(d)] ?? 0), 0));
  /** Se escribió la cantidad de al menos una denominación (aunque sea 0). */
  protected readonly contoAlgo = computed(() => Object.values(this.cantidades()).some(c => c != null));

  /** Al abrirse, el foco va al billete de mayor valor. */
  ngAfterViewInit(): void {
    this.elemento.nativeElement.querySelector<HTMLInputElement>('#cc-' + clave(BILLETES[0]))?.focus();
  }

  /** @param d denominación @returns cantidad × valor */
  protected subtotal(d: Denominacion): number {
    return d.valor * (this.cantidades()[clave(d)] ?? 0);
  }

  /** @param d denominación; @param cantidad unidades contadas (null = vacía) */
  protected contarDenominacion(d: Denominacion, cantidad: number | null): void {
    this.cantidades.update(c => ({ ...c, [clave(d)]: cantidad == null ? undefined : Math.max(0, Math.trunc(cantidad)) }));
  }

  /** @param d denominación; @param texto referencia de la fila */
  protected referir(d: Denominacion, texto: string): void {
    this.referencias.update(r => ({ ...r, [clave(d)]: texto }));
  }

  /** @param m medio; @param valor contado */
  protected contar(m: MedioPago, valor: number | null): void {
    this.contado.update(c => ({ ...c, [m]: valor ?? undefined }));
  }

  /** @returns filas con cantidad o referencia, para enviar al backend */
  private conteo(): ConteoDenominacion[] {
    return [...BILLETES, ...MONEDAS]
      .map(d => ({ tipo: d.tipo, valor: d.valor, cantidad: this.cantidades()[clave(d)] ?? 0,
        referencia: this.referencias()[clave(d)]?.trim() || undefined }))
      .filter(f => f.cantidad > 0 || f.referencia);
  }

  protected cerrar(): void {
    const c = this.contado();
    const otros: Partial<Record<MedioPago, number>> = {};
    this.otrosMedios.forEach(m => (otros[m] = c[m] ?? 0));
    const conteo = this.conteo();
    this.enviando.set(true);
    // Sin efectivo (todo en 0) no hay filas: se envía el total 0. Con filas, el backend suma el conteo.
    this.caja.cerrar(this.turnoId(), conteo.length ? null : 0, otros, this.observacion().trim() || null, conteo.length ? conteo : null)
      .subscribe({
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
