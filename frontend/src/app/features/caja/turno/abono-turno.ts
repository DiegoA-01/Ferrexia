import { AfterViewInit, Component, ElementRef, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Cliente, MedioPago } from '../../../core/api/modelos';
import { CarteraService } from '../../../core/servicios/cartera.service';
import { ClientesService } from '../../../core/servicios/clientes.service';
import { Avisos } from '../../../core/ui/avisos';
import { Cifra } from '../../../core/ui/cifra';
import { htmlRecibo, imprimirHtml } from '../../pos/tiquete';
import { MEDIOS } from '../caja';

/**
 * Abono de un cliente en el turno (spec 007): entra a la caja del turno y deja recibo. "Mi turno" (spec 018) lo muestra
 * solo cuando el cajero elige esta acción y la empresa tiene el módulo de cartera.
 */
@Component({
  selector: 'sc-abono-turno',
  imports: [Cifra, FormsModule],
  template: `
    <section class="tarjeta">
      <h2>Abono de cliente</h2>
      <div class="formulario">
        <div class="campo ancho"><label for="ab-cliente">Cliente</label>
          <div class="fila"><input id="ab-cliente" class="entrada" placeholder="Documento o nombre" [ngModel]="buscarCliente()" (ngModelChange)="buscarCliente.set($event)" (keydown.enter)="buscarClientes()">
            <button type="button" class="boton" (click)="buscarClientes()">Buscar</button></div>
          @if (clientes().length && !clienteAbono()) {
            <ul class="lista">@for (c of clientes(); track c.id) {
              <li><button type="button" class="boton chico" (click)="clienteAbono.set(c)">{{ c.nombre }} · {{ c.numeroDocumento }}</button></li>
            }</ul>
          }
          @if (clienteAbono(); as c) { <span class="sub">Abona: <strong>{{ c.nombre }}</strong> <button type="button" class="boton chico" (click)="clienteAbono.set(null)">Cambiar</button></span> }
        </div>
        <div class="campo"><label for="ab-valor">Valor</label>
          <input scCifra id="ab-valor" min="0" class="entrada" [ngModel]="valorAbono()" (ngModelChange)="valorAbono.set($event)"></div>
        <div class="campo"><label for="ab-medio">Medio</label>
          <select id="ab-medio" class="entrada" [ngModel]="medioAbono()" (ngModelChange)="medioAbono.set($event)">
            @for (m of medios; track m) { <option [ngValue]="m">{{ textoMedio(m) }}</option> }
          </select></div>
        <div class="campo"><label for="ab-ref">Referencia</label>
          <input id="ab-ref" class="entrada" maxlength="40" [placeholder]="medioAbono() === 'EFECTIVO' ? 'Opcional' : 'Obligatoria'" [ngModel]="referenciaAbono()" (ngModelChange)="referenciaAbono.set($event)"></div>
      </div>
      <button type="button" class="boton primario" [disabled]="!clienteAbono() || (valorAbono() ?? 0) <= 0 || enviando() || (medioAbono() !== 'EFECTIVO' && !referenciaAbono().trim())" (click)="abonar()">Registrar abono e imprimir recibo</button>
    </section>
  `,
  styles: `
    h2 { margin: 0 0 8px; }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .formulario { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 12px; .ancho { grid-column: 1 / -1; } }
    .lista { list-style: none; padding: 0; margin: 12px 0 0; display: grid; gap: 4px; }
  `,
})
export class AbonoTurno implements AfterViewInit {
  private readonly cartera = inject(CarteraService);
  private readonly clientesService = inject(ClientesService);
  private readonly avisos = inject(Avisos);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);

  /** El abono quedó registrado: "Mi turno" vuelve a leer el turno. */
  readonly registrado = output<void>();

  protected readonly medios = Object.keys(MEDIOS) as MedioPago[];
  protected readonly buscarCliente = signal('');
  protected readonly clientes = signal<Cliente[]>([]);
  protected readonly clienteAbono = signal<Cliente | null>(null);
  protected readonly valorAbono = signal<number | null>(null);
  protected readonly medioAbono = signal<MedioPago>('EFECTIVO');
  protected readonly referenciaAbono = signal('');
  protected readonly enviando = signal(false);

  /** Al abrirse, el foco va a la búsqueda del cliente. */
  ngAfterViewInit(): void {
    this.elemento.nativeElement.querySelector<HTMLInputElement>('#ab-cliente')?.focus();
  }

  protected buscarClientes(): void {
    const q = this.buscarCliente().trim();
    if (q) {
      this.clienteAbono.set(null);
      this.clientesService.buscar(q, true).subscribe({ next: p => this.clientes.set(p.contenido ?? []), error: e => this.avisos.error(e) });
    }
  }

  /** Registra el abono e imprime el recibo. */
  protected abonar(): void {
    this.enviando.set(true);
    this.cartera.abonar(this.clienteAbono()!.id!, this.valorAbono()!, this.medioAbono(), this.referenciaAbono().trim() || null).subscribe({
      next: ab => {
        this.enviando.set(false);
        this.avisos.ok(`Abono ${ab.consecutivo} registrado. Le quedan ${ab.saldoPendiente} por pagar.`);
        imprimirHtml(htmlRecibo(ab));
        this.clienteAbono.set(null);
        this.valorAbono.set(null);
        this.referenciaAbono.set('');
        this.clientes.set([]);
        this.registrado.emit();
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
