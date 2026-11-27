import { Component, computed, inject, input, signal } from '@angular/core';

import { MovimientoCaja as MovimientoApi, Venta } from '../../../core/api/modelos';
import { FechaPipe, PesosPipe } from '../../../core/formato/formato';
import { VentaService } from '../../../core/servicios/venta.service';
import { Avisos } from '../../../core/ui/avisos';
import { MEDIOS } from '../caja';

/**
 * Movimiento del libro con los datos de venta que agrega el backend (referencia, consecutivo y total). Tipo local
 * mínimo hasta regenerar los tipos de la API (`npm run api:tipos`).
 */
export type MovimientoTurno = MovimientoApi & {
  referenciaTipo?: string | null; referenciaId?: number | null; consecutivo?: string | null; totalVenta?: number | null;
};

/** Una venta del turno: todos sus pagos juntos, una sola vez. */
export interface VentaDelTurno {
  ventaId: number;
  consecutivo: string;
  /** Hora de su primer cobro. */
  creadoEn: string | undefined;
  /** Total de la venta, calculado por el backend. */
  total: number | null;
  /** Cobros (un movimiento por medio de pago). */
  cobros: MovimientoTurno[];
  /** Anulaciones y ajustes de pago de esta misma venta. */
  relacionados: MovimientoTurno[];
  anulada: boolean;
  /** Último id de movimiento, para ordenar. */
  orden: number;
}

/** Nombre claro de cada tipo de movimiento que no es una venta. */
const TIPOS: Record<string, string> = {
  BASE: 'Base', RETIRO: 'Retiro', GASTO: 'Gasto', INGRESO: 'Ingreso', DEVOLUCION: 'Devolución', ANULACION: 'Anulación',
  ABONO_CARTERA: 'Abono de cartera', AJUSTE_PAGO: 'Ajuste de pago', COBRO_DOMICILIO: 'Cobro de domicilio', VENTA: 'Venta',
};

/** Movimientos de una venta que se muestran dentro de su detalle y no en "Otros movimientos". */
const DE_LA_VENTA = ['ANULACION', 'AJUSTE_PAGO'];

/**
 * Movimientos del turno (Mi turno): cada venta sale una sola vez, con su consecutivo, hora, total y el resumen de medios
 * ("Efectivo + Tarjeta crédito"); "Ver detalle" despliega cada pago y, para efectivo, lo recibido y el cambio. Los demás
 * movimientos (retiros, gastos, ingresos, devoluciones, abonos, cobros de domicilio...) van aparte, en su grupo. Lo más
 * reciente arriba. Solo agrupa y muestra: el total de la venta lo entrega el backend.
 */
@Component({
  selector: 'sc-movimientos-turno',
  imports: [PesosPipe, FechaPipe],
  template: `
    @if (ventas().length) {
      <section class="tarjeta">
        <h2>Ventas del turno</h2>
        <ul class="sin-lista">@for (v of ventas(); track v.ventaId) {
          <li class="venta" [class.anulada]="v.anulada">
            <div class="cabeza">
              <div class="datos">
                <strong>{{ v.consecutivo }}</strong>
                <span class="sub">{{ v.creadoEn | fecha: 'hora' }}</span>
                @if (v.anulada) { <span class="chip rojo">Anulada</span> }
              </div>
              <div class="monto">
                <strong>{{ v.total | pesos }}</strong>
                <span class="sub">{{ resumenMedios(v) }}</span>
              </div>
              <button type="button" class="boton chico" [attr.aria-expanded]="abierta() === v.ventaId" (click)="alternar(v)">
                {{ abierta() === v.ventaId ? 'Ocultar detalle' : 'Ver detalle' }}</button>
            </div>
            @if (abierta() === v.ventaId) {
              <div class="desglose">
                <ul class="sin-lista">@for (c of v.cobros; track c.id) {
                  <li><span class="chip naranja">{{ medio(c) }}</span><span class="num">{{ c.monto | pesos }}</span>
                    @if (c.medioPago === 'EFECTIVO' && recibido()) {
                      <span class="sub">recibió {{ recibido() | pesos }}@if (cambio()) { · cambio {{ cambio() | pesos }} }</span>
                    }</li>
                }</ul>
                @for (r of v.relacionados; track r.id) {
                  <p class="sub sin-margen"><strong>{{ tipo(r) }}</strong> {{ r.creadoEn | fecha: 'hora' }} · {{ medio(r) }} · {{ r.monto | pesos }}@if (r.motivo) { · {{ r.motivo }} }</p>
                }
              </div>
            }
          </li>
        }</ul>
      </section>
    }
    @if (otros().length) {
      <section class="tarjeta">
        <h2>Otros movimientos</h2>
        <ul class="sin-lista">@for (m of otros(); track m.id) {
          <li class="otro"><span>{{ m.tipo }}@if (m.categoria) { · {{ m.categoria }} } · {{ m.motivo }}
            <span class="sub">{{ m.creadoEn | fecha: 'hora' }}</span></span>
            <span class="num">{{ m.monto | pesos }}</span></li>
        }</ul>
      </section>
    }
  `,
  styles: `
    h2 { margin: 0 0 8px; }
    .sin-lista { list-style: none; padding: 0; margin: 0; display: grid; gap: 8px; }
    .sub { font-size: 12px; color: var(--sc-texto-2); }
    .venta { border: 1px solid var(--sc-borde); border-radius: 12px; padding: 10px 12px; }
    .venta.anulada strong { text-decoration: line-through; color: var(--sc-texto-3); }
    .cabeza { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px 12px; }
    .datos, .monto { display: grid; gap: 2px; }
    .monto { text-align: right; flex: 1 1 140px; }
    .desglose { margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--sc-borde); display: grid; gap: 8px;
      li { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 10px; justify-content: space-between; } }
    .otro { display: flex; justify-content: space-between; gap: 12px; }
    .otro > span:first-child { display: grid; }
  `,
})
export class MovimientosTurno {
  private readonly ventasApi = inject(VentaService);
  private readonly avisos = inject(Avisos);

  /** Libro de caja del turno (sin la base). */
  readonly movimientos = input.required<MovimientoTurno[]>();

  /** Venta con el desglose abierto. */
  protected readonly abierta = signal<number | null>(null);
  private readonly detalle = signal<Venta | null>(null);
  /** Efectivo que entregó el cliente en la venta abierta, si el dato existe. */
  protected readonly recibido = computed(() => this.detalle()?.pagos?.find(p => p.medio === 'EFECTIVO')?.recibido ?? null);
  /** Cambio devuelto en la venta abierta. */
  protected readonly cambio = computed(() => this.detalle()?.cambio ?? null);

  /** Ventas del turno, la más reciente primero; cada una una sola vez (se agrupa por el id de la venta). */
  protected readonly ventas = computed<VentaDelTurno[]>(() => {
    const mapa = new Map<number, VentaDelTurno>();
    for (const m of this.movimientos()) {
      if (m.tipo === 'VENTA' && m.referenciaTipo === 'VENTA' && m.referenciaId != null) {
        const v = mapa.get(m.referenciaId) ?? { ventaId: m.referenciaId, consecutivo: m.consecutivo ?? `Venta ${m.referenciaId}`,
          creadoEn: m.creadoEn, total: m.totalVenta ?? null, cobros: [], relacionados: [], anulada: false, orden: 0 };
        v.cobros.push(m);
        v.orden = Math.max(v.orden, m.id ?? 0);
        mapa.set(m.referenciaId, v);
      }
    }
    for (const m of this.movimientos()) {
      const v = m.referenciaId != null && m.referenciaTipo === 'VENTA' ? mapa.get(m.referenciaId) : undefined;
      if (v && m.tipo && DE_LA_VENTA.includes(m.tipo)) {
        v.relacionados.push(m);
        v.anulada = v.anulada || m.tipo === 'ANULACION';
      }
    }
    return [...mapa.values()].sort((a, b) => b.orden - a.orden);
  });

  /** Movimientos que no son de una venta (ni su anulación o ajuste), el más reciente primero. */
  protected readonly otros = computed(() => {
    const enVentas = new Set(this.ventas().map(v => v.ventaId));
    return this.movimientos()
      .filter(m => !(m.referenciaTipo === 'VENTA' && m.referenciaId != null && enVentas.has(m.referenciaId)
        && m.tipo && ['VENTA', ...DE_LA_VENTA].includes(m.tipo)))
      .sort((a, b) => (b.id ?? 0) - (a.id ?? 0));
  });

  /** @param v venta; abre o cierra su desglose y, al abrir, lee la venta para saber lo recibido y el cambio */
  protected alternar(v: VentaDelTurno): void {
    this.detalle.set(null);
    if (this.abierta() === v.ventaId) {
      this.abierta.set(null);
      return;
    }
    this.abierta.set(v.ventaId);
    this.ventasApi.obtener(v.ventaId).subscribe({
      next: d => this.abierta() === v.ventaId && this.detalle.set(d),
      error: e => this.avisos.error(e),
    });
  }

  /** @param v venta @returns medios usados sin repetir ("Efectivo + Tarjeta crédito") */
  protected resumenMedios(v: VentaDelTurno): string {
    return [...new Set(v.cobros.map(c => this.medio(c)))].join(' + ');
  }

  /** @param m movimiento @returns nombre del medio de pago */
  protected medio(m: MovimientoTurno): string {
    return m.medioPago ? MEDIOS[m.medioPago] ?? m.medioPago : '—';
  }

  /** @param m movimiento @returns nombre del tipo */
  protected tipo(m: MovimientoTurno): string {
    return TIPOS[m.tipo ?? ''] ?? m.tipo ?? '';
  }
}
