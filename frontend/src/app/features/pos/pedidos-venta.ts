import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { EstadoPedidoVenta, Pagina, PedidoVenta } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe } from '../../core/formato/formato';
import { VentaService } from '../../core/servicios/venta.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { ROLES_COBRAR } from './pos';

/** Texto del estado, texto del filtro y color. "Descartado" se muestra como eliminado (spec 016). */
const ESTADOS: Record<EstadoPedidoVenta, { texto: string; filtro: string; tono: string }> = {
  PENDIENTE_COBRO: { texto: 'Por cobrar', filtro: 'Por cobrar', tono: 'ambar' },
  COBRADO: { texto: 'Cobrado', filtro: 'Cobrados', tono: 'verde' },
  DESCARTADO: { texto: 'Eliminado', filtro: 'Eliminados', tono: 'gris' },
};

/**
 * Pedidos de venta (spec 004/005): el vendedor ve los suyos; el cajero ve los pendientes y los cobra en el POS. Un
 * pedido no guarda montos: al cobrarlo se usan los precios del día.
 */
@Component({
  selector: 'sc-pedidos-venta',
  imports: [FormsModule, RouterLink, FechaPipe, NumeroPipe],
  template: `
    <div class="pagina">
      <div class="encabezado"><div><h1>Pedidos de venta</h1>
        <p>Pedidos armados por los vendedores, sin cobrar. No descuentan stock hasta que el cajero los cobra.</p></div>
        <a class="boton primario" routerLink="/app/pos"><span class="material-symbols-outlined">add_shopping_cart</span> {{ cobra() ? 'Ir al POS' : 'Nuevo pedido' }}</a></div>
      <section class="tarjeta sin-relleno">
        <div class="filtros">
          <label for="pv-estado">Estado</label>
          <select id="pv-estado" class="entrada" [ngModel]="estado()" (ngModelChange)="estado.set($event); buscar()">
            <option [ngValue]="null">Todos</option>
            @for (e of estados; track e) { <option [ngValue]="e">{{ filtro(e) }}</option> }
          </select>
        </div>
        @if (descartando(); as d) {
          <div class="descarte" role="dialog" aria-labelledby="pv-descarte-titulo">
            <strong id="pv-descarte-titulo">Descartar el pedido {{ d.consecutivo }}</strong>
            <label for="pv-motivo">¿Por qué se descarta? Queda en "Eliminados" con tu nombre y la hora.</label>
            <textarea id="pv-motivo" class="entrada" rows="2" maxlength="300" placeholder="El cliente desistió"
              [ngModel]="motivo()" (ngModelChange)="motivo.set($event)"></textarea>
            <div class="fila fin">
              <button type="button" class="boton chico" (click)="descartando.set(null)">Volver</button>
              <button type="button" class="boton chico primario" [disabled]="motivo().trim().length < 3 || enviando()"
                (click)="confirmarDescarte()">Descartar pedido</button>
            </div>
          </div>
        }
        <div class="tabla-contenedor"><table class="tabla">
          <thead><tr><th>Pedido</th><th>Fecha</th><th>Cliente</th><th>Vendedor</th><th>Productos</th>
            @if (verEliminados()) { <th>Eliminado por</th><th>Cuándo</th><th>Motivo</th> } @else { <th>Estado</th><th></th> }</tr></thead>
          <tbody>
            @for (p of pagina()?.contenido ?? []; track p.id) {
              <tr>
                <td><strong>{{ p.consecutivo }}</strong>@if (p.nota) { <span class="sub">{{ p.nota }}</span> }</td>
                <td>{{ p.creadoEn | fecha: 'hora' }}</td><td>{{ p.cliente }}</td><td>{{ p.vendedor }}</td>
                <td>@for (l of p.lineas ?? []; track $index) { <span class="sub">{{ l.cantidad | numero }} {{ l.unidad }} · {{ l.nombre }}</span> }</td>
                @if (verEliminados()) {
                  @if (p.descartadoEn) {
                    <td>{{ p.descartadoPor }}</td><td>{{ p.descartadoEn | fecha: 'hora' }}</td><td>{{ p.motivoDescarte }}</td>
                  } @else {
                    <td colspan="3" class="sub">Sin dato: se eliminó antes de que se guardara quién y por qué.</td>
                  }
                } @else {
                  <td><span class="chip {{ tono(p.estado) }}">{{ texto(p.estado) }}</span>
                    @if (p.estado === 'DESCARTADO' && p.motivoDescarte) { <span class="sub">{{ p.motivoDescarte }}</span> }</td>
                  <td class="num">@if (p.estado === 'PENDIENTE_COBRO') {
                    <div class="fila fin">
                      @if (cobra()) { <a class="boton chico primario" routerLink="/app/pos" [queryParams]="{ pedido: p.id }">Cobrar</a> }
                      <button type="button" class="boton chico" (click)="descartar(p)">Descartar</button>
                    </div>
                  }</td>
                }
              </tr>
            } @empty {
              <tr><td [attr.colspan]="verEliminados() ? 8 : 7" class="vacio">{{ pagina() ? 'No hay pedidos con este filtro.' : 'Cargando…' }}</td></tr>
            }
          </tbody>
        </table></div>
      </section>
    </div>
  `,
  styles: `
    .filtros { display: flex; gap: 10px; align-items: center; padding: 14px 20px; border-bottom: 1px solid var(--sc-borde); select { max-width: 220px; } }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .fin { justify-content: flex-end; gap: 6px; }
    .chip.gris { background: #efefef; color: var(--sc-texto-2); }
    .descarte { display: flex; flex-direction: column; gap: 8px; margin: 14px 20px; padding: 14px 16px;
      border: 1px solid #fed7aa; border-radius: 12px; background: #fff7ed; }
  `,
})
export class PedidosVenta implements OnInit {
  private readonly ventas = inject(VentaService);
  private readonly avisos = inject(Avisos);
  private readonly sesion = inject(SesionService);

  protected readonly estados = Object.keys(ESTADOS) as EstadoPedidoVenta[];
  protected readonly estado = signal<EstadoPedidoVenta | null>('PENDIENTE_COBRO');
  protected readonly pagina = signal<Pagina<PedidoVenta> | null>(null);
  protected readonly cobra = computed(() => this.sesion.tieneRol(ROLES_COBRAR) && !this.sesion.soloLectura());
  /** Filtro "Eliminados": muestra quién, cuándo y por qué (spec 016). */
  protected readonly verEliminados = computed(() => this.estado() === 'DESCARTADO');
  /** Pedido que se está por descartar (pide el motivo). */
  protected readonly descartando = signal<PedidoVenta | null>(null);
  protected readonly motivo = signal('');
  protected readonly enviando = signal(false);

  ngOnInit(): void {
    this.buscar();
  }

  protected buscar(): void {
    this.ventas.pedidos(this.estado()).subscribe({ next: p => this.pagina.set(p), error: e => this.avisos.error(e) });
  }

  /** @param p pedido pendiente: abre el cuadro que pide el motivo */
  protected descartar(p: PedidoVenta): void {
    this.motivo.set('');
    this.descartando.set(p);
  }

  /** Descarta con el motivo escrito; el pedido pasa a "Eliminados". */
  protected confirmarDescarte(): void {
    const p = this.descartando();
    const motivo = this.motivo().trim();
    if (!p || motivo.length < 3 || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.ventas.descartarPedido(p.id!, motivo).subscribe({
      next: () => {
        this.enviando.set(false);
        this.descartando.set(null);
        this.avisos.ok(`Pedido ${p.consecutivo} descartado. Queda en "Eliminados".`);
        this.buscar();
      },
      error: e => {
        this.enviando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param e estado @returns texto del filtro */
  protected filtro(e: EstadoPedidoVenta): string {
    return ESTADOS[e].filtro;
  }

  /** @param e estado @returns texto */
  protected texto(e: EstadoPedidoVenta | undefined): string {
    return e ? ESTADOS[e].texto : '';
  }

  /** @param e estado @returns color */
  protected tono(e: EstadoPedidoVenta | undefined): string {
    return e ? ESTADOS[e].tono : 'gris';
  }
}
