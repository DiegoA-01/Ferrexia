import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { MedioPagoVenta, Venta } from '../../core/api/modelos';
import { PesosPipe } from '../../core/formato/formato';
import { VentaService } from '../../core/servicios/venta.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { Cifra } from '../../core/ui/cifra';
import { AutorizacionPin } from '../caja/autorizacion-pin';
import { MEDIOS_VENTA } from '../pos/pos';

/** Medios que entran a la caja: el crédito y el saldo a favor se corrigen en la cartera. */
export const MEDIOS_CAJA: MedioPagoVenta[] = ['EFECTIVO', 'TARJETA_DEBITO', 'TARJETA_CREDITO', 'TRANSFERENCIA', 'BILLETERA'];

/** Un pago mientras se edita. */
interface PagoEnEdicion {
  medio: MedioPagoVenta;
  monto: number | null;
  referencia: string;
}

/**
 * Cambiar el método de pago de una factura (spec 029, D16): el total queda fijo y los pagos nuevos deben sumarlo
 * exactamente. Con el permiso de cambiar pagos se guarda directo; sin él, pide el PIN del supervisor (solo el cajero en
 * su turno abierto). La caja la ajusta el backend; la pantalla solo muestra cuánto falta o sobra.
 */
@Component({
  selector: 'sc-cambio-pago',
  imports: [FormsModule, Cifra, PesosPipe, AutorizacionPin],
  template: `
    <div class="cambio" role="group" aria-label="Cambiar método de pago">
      <strong>Cambiar método de pago</strong>
      <p class="sub">El total de la factura no cambia: {{ venta().totalAPagar | pesos }}. La diferencia por medio queda en la caja como ajuste.</p>
      @for (p of pagos(); track $index; let i = $index) {
        <div class="pago">
          <select class="entrada" [attr.aria-label]="'Medio del pago ' + (i + 1)" [ngModel]="p.medio" (ngModelChange)="cambiar(i, 'medio', $event)">
            @for (m of medios; track m) { <option [ngValue]="m">{{ texto(m) }}</option> }
          </select>
          <input scCifra class="entrada num" [attr.aria-label]="'Monto del pago ' + (i + 1)" [ngModel]="p.monto" (ngModelChange)="cambiar(i, 'monto', $event)">
          <input class="entrada" maxlength="40" placeholder="Voucher o referencia (opcional)" [attr.aria-label]="'Referencia del pago ' + (i + 1)"
            [ngModel]="p.referencia" (ngModelChange)="cambiar(i, 'referencia', $event)">
          @if (pagos().length > 1) { <button type="button" class="boton chico" (click)="quitar(i)">Quitar</button> }
        </div>
      }
      <div class="fila">
        @if (pagos().length < 5) { <button type="button" class="boton chico" (click)="agregar()">Agregar pago</button> }
        @if (diferencia() !== 0) {
          <span class="chip ambar">{{ diferencia() > 0 ? 'Faltan' : 'Sobran' }} {{ (diferencia() > 0 ? diferencia() : -diferencia()) | pesos }}</span>
        } @else { <span class="chip verde">Cuadra con el total</span> }
      </div>
      <div class="campo"><label for="cp-motivo">Motivo</label>
        <input id="cp-motivo" class="entrada" maxlength="300" placeholder="Marcó efectivo y era tarjeta…" [ngModel]="motivo()" (ngModelChange)="motivo.set($event)"></div>
      @if (pidiendoPin()) {
        <sc-autorizacion-pin accion="CAMBIAR_PAGO" [turnoId]="turnoId()!" [referenciaId]="venta().id!" (autorizado)="enviar($event.token)" (cancelado)="pidiendoPin.set(false)" />
      } @else {
        <div class="fila">
          <button type="button" class="boton primario" [disabled]="!valido() || enviando()" (click)="guardar()">
            {{ conPermiso() ? 'Guardar cambio' : 'Guardar cambio (pide PIN)' }}</button>
          <button type="button" class="boton" (click)="cancelado.emit()">Cancelar</button>
        </div>
      }
    </div>
  `,
  styles: `
    .cambio { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--sc-borde); border-radius: 12px; }
    .sub { margin: 0; font-size: 13px; color: var(--sc-texto-2); }
    .pago { display: grid; grid-template-columns: minmax(140px, 180px) 140px 1fr auto; gap: 8px; align-items: center; }
    @media (max-width: 640px) { .pago { grid-template-columns: 1fr 1fr; } }
  `,
})
export class CambioPago implements OnInit {
  private readonly ventas = inject(VentaService);
  private readonly sesion = inject(SesionService);
  private readonly avisos = inject(Avisos);

  /** Factura que se corrige. */
  readonly venta = input.required<Venta>();
  /** Turno abierto de la sesión, para pedir el PIN del supervisor ({@code null} si no tiene). */
  readonly turnoId = input<number | null>(null);
  readonly cambiado = output<Venta>();
  readonly cancelado = output<void>();

  protected readonly medios = MEDIOS_CAJA;
  protected readonly pagos = signal<PagoEnEdicion[]>([]);
  protected readonly motivo = signal('');
  protected readonly pidiendoPin = signal(false);
  protected readonly enviando = signal(false);
  /** Con el permiso no pide PIN. */
  protected readonly conPermiso = computed(() => this.sesion.tienePermiso('VENTA_CAMBIAR_PAGO'));
  /** Lo que falta (positivo) o sobra (negativo) para llegar al total; solo para mostrar, el backend lo valida. */
  protected readonly diferencia = computed(() => {
    const suma = this.pagos().reduce((s, p) => s + Math.round((p.monto ?? 0) * 100), 0);
    return (Math.round((this.venta().totalAPagar ?? 0) * 100) - suma) / 100;
  });
  protected readonly valido = computed(() => this.motivo().trim().length >= 5 && this.diferencia() === 0
    && this.pagos().every(p => (p.monto ?? 0) > 0) && (this.conPermiso() || this.turnoId() != null));

  ngOnInit(): void {
    const actuales = (this.venta().pagos ?? []).filter(p => MEDIOS_CAJA.includes(p.medio!));
    this.pagos.set(actuales.length ? actuales.map(p => ({ medio: p.medio!, monto: p.monto ?? null, referencia: p.referencia ?? '' }))
      : [{ medio: 'EFECTIVO', monto: this.venta().totalAPagar ?? null, referencia: '' }]);
  }

  /** @param m medio @returns su nombre */
  protected texto(m: MedioPagoVenta): string {
    return MEDIOS_VENTA[m] ?? m;
  }

  /** @param i pago; @param campo campo; @param valor valor nuevo */
  protected cambiar<K extends keyof PagoEnEdicion>(i: number, campo: K, valor: PagoEnEdicion[K]): void {
    this.pagos.update(l => l.map((p, j) => (j === i ? { ...p, [campo]: valor } : p)));
  }

  protected agregar(): void {
    this.pagos.update(l => [...l, { medio: 'TARJETA_DEBITO', monto: this.diferencia() > 0 ? this.diferencia() : null, referencia: '' }]);
  }

  /** @param i pago que se quita */
  protected quitar(i: number): void {
    this.pagos.update(l => l.filter((_, j) => j !== i));
  }

  /** Guarda directo con el permiso; si no, pide el PIN del supervisor. */
  protected guardar(): void {
    if (!this.valido()) {
      return;
    }
    if (this.conPermiso()) {
      this.enviar(null);
    } else {
      this.pidiendoPin.set(true);
    }
  }

  /** @param autorizacion permiso de un solo uso del supervisor, o {@code null} con el permiso */
  protected enviar(autorizacion: string | null): void {
    this.pidiendoPin.set(false);
    this.enviando.set(true);
    this.ventas.cambiarPago(this.venta().id!, {
      pagos: this.pagos().map(p => ({ medio: p.medio, monto: p.monto!, referencia: p.referencia.trim() || undefined })),
      motivo: this.motivo().trim(), autorizacion: autorizacion ?? undefined,
    }).subscribe({
      next: v => {
        this.enviando.set(false);
        this.avisos.ok(`Pago de ${v.consecutivo} corregido.`);
        this.cambiado.emit(v);
      },
      error: e => {
        this.enviando.set(false);
        this.avisos.error(e);
      },
    });
  }
}
