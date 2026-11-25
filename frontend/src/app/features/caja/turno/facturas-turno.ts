import { Component, OnInit, inject, input, signal } from '@angular/core';

import { MedioPagoVenta, Pagina, Venta, VentaResumen } from '../../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../../core/formato/formato';
import { VentaService } from '../../../core/servicios/venta.service';
import { Avisos } from '../../../core/ui/avisos';
import { MEDIOS_VENTA } from '../../pos/pos';
import { imprimirTiquete } from '../../pos/tiquete';

/**
 * Facturas del turno abierto (spec 021): lista paginada de 20 con número, hora, cliente, total, medios y estado, y por
 * fila reimprimir (sale como COPIA), ver el detalle con líneas y pagos, y editar (llega con la spec 029). El backend solo
 * deja al cajero listar su propio turno abierto; lo esperado de la caja sigue oculto (cierre ciego, D20).
 */
@Component({
  selector: 'sc-facturas-turno',
  imports: [PesosPipe, NumeroPipe, FechaPipe],
  template: `
    <section class="tarjeta sin-relleno">
      <h2 class="titulo">Facturas del turno</h2>
      <div class="tabla-contenedor"><table class="tabla">
        <thead><tr><th>Número</th><th>Hora</th><th>Cliente</th><th class="num">Total</th><th>Medio de pago</th><th>Estado</th><th></th></tr></thead>
        <tbody>
          @for (v of pagina()?.contenido ?? []; track v.id) {
            <tr [class.anulada]="v.estado === 'ANULADA'">
              <td><strong>{{ v.consecutivo }}</strong></td>
              <td>{{ v.confirmadaEn | fecha: 'hora' }}</td>
              <td>{{ v.cliente }}</td>
              <td class="num">{{ v.totalAPagar | pesos }}</td>
              <td>{{ textoMedios(v.medios) }}</td>
              <td>{{ v.estado === 'ANULADA' ? 'Anulada' : 'Confirmada' }}</td>
              <td class="num acciones">
                <button type="button" class="boton chico" (click)="reimprimir(v)">Reimprimir</button>
                <button type="button" class="boton chico" (click)="ver(v)">Detalle</button>
                <button type="button" class="boton chico" disabled title="Disponible con la spec 029">Editar</button>
              </td>
            </tr>
          } @empty {
            <tr><td colspan="7" class="vacio">{{ pagina() ? 'Todavía no hay facturas en este turno.' : 'Cargando…' }}</td></tr>
          }
        </tbody>
      </table></div>
      @if ((pagina()?.totalPaginas ?? 0) > 1) {
        <div class="fila paginacion">
          <button type="button" class="boton chico" [disabled]="pagina()!.pagina === 0" (click)="cargar(pagina()!.pagina! - 1)">Anterior</button>
          <span>Página {{ pagina()!.pagina! + 1 }} de {{ pagina()!.totalPaginas }}</span>
          <button type="button" class="boton chico" [disabled]="pagina()!.pagina! + 1 >= pagina()!.totalPaginas!" (click)="cargar(pagina()!.pagina! + 1)">Siguiente</button>
        </div>
      }
    </section>

    @if (detalle(); as d) {
      <section class="tarjeta detalle" aria-labelledby="ft-detalle">
        <div class="fila entre"><h2 id="ft-detalle">Factura {{ d.consecutivo }}</h2>
          <button type="button" class="boton chico" (click)="detalle.set(null)">Cerrar</button></div>
        <p class="sin-margen">{{ d.confirmadaEn | fecha: 'hora' }} · {{ d.cliente }}@if (d.vendedor) { · vendió {{ d.vendedor }} }</p>
        <div class="tabla-contenedor"><table class="tabla">
          <thead><tr><th>Producto</th><th class="num">Cantidad</th><th class="num">Precio</th><th class="num">Total</th></tr></thead>
          <tbody>@for (l of d.lineas ?? []; track $index) {
            <tr><td>{{ l.nombre }}</td><td class="num">{{ l.cantidad | numero }} {{ l.unidad }}</td>
              <td class="num">{{ l.precioUnitario | pesos }}</td><td class="num">{{ l.totalLinea | pesos }}</td></tr>
          }</tbody>
        </table></div>
        <ul class="sin-margen">@for (p of d.pagos ?? []; track $index) {
          <li>{{ textoMedio(p.medio) }}: {{ p.monto | pesos }}</li>
        }</ul>
        <p class="sin-margen"><strong>Total pagado: {{ d.totalAPagar | pesos }}</strong></p>
      </section>
    }
  `,
  styles: `
    .titulo { margin: 0; padding: 14px 20px 0; }
    .acciones { white-space: nowrap; button + button { margin-left: 4px; } }
    tr.anulada td { color: var(--sc-texto-3); }
    .detalle { display: grid; gap: 10px; h2 { margin: 0; } }
    .paginacion { padding: 10px 20px; align-items: center; }
  `,
})
export class FacturasTurno implements OnInit {
  private readonly ventas = inject(VentaService);
  private readonly avisos = inject(Avisos);

  /** Turno abierto del cajero. */
  readonly turnoId = input.required<number>();

  protected readonly pagina = signal<Pagina<VentaResumen> | null>(null);
  protected readonly detalle = signal<Venta | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  /** @param pagina página (20 por página) */
  protected cargar(pagina = 0): void {
    this.ventas.buscar({ turnoId: this.turnoId(), pagina }).subscribe({
      next: p => this.pagina.set(p),
      error: e => this.avisos.error(e),
    });
  }

  /** @param v factura; el tiquete sale como COPIA y la reimpresión queda en la auditoría */
  protected reimprimir(v: VentaResumen): void {
    this.ventas.tiquete(v.id!).subscribe({ next: t => imprimirTiquete(t), error: e => this.avisos.error(e) });
  }

  /** @param v factura; abre su detalle con líneas y pagos */
  protected ver(v: VentaResumen): void {
    this.ventas.obtener(v.id!).subscribe({ next: d => this.detalle.set(d), error: e => this.avisos.error(e) });
  }

  /** @param medios medios usados @returns texto ("Efectivo y Transferencia") */
  protected textoMedios(medios: MedioPagoVenta[] | undefined): string {
    const textos = (medios ?? []).map(m => MEDIOS_VENTA[m] ?? m);
    return textos.length ? textos.join(' y ') : '—';
  }

  /** @param m medio @returns texto */
  protected textoMedio(m: MedioPagoVenta | undefined): string {
    return m ? MEDIOS_VENTA[m] ?? m : '—';
  }
}
