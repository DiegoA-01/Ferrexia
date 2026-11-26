import { Component, OnInit, inject, input, signal } from '@angular/core';

import { EnlaceCotizacion } from '../../core/api/modelos';
import { FechaPipe } from '../../core/formato/formato';
import { CotizacionesService } from '../../core/servicios/cotizaciones.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Spec 031: enlaces para que cada proveedor invitado cotice sin cuenta. Se crean al abrir la RFQ y se envían por
 * correo; aquí se copian, se mandan por WhatsApp o se regeneran (el anterior deja de servir).
 */
@Component({
  selector: 'sc-enlaces-rfq',
  imports: [FechaPipe],
  template: `
    <section class="tarjeta sin-relleno enlaces">
      <div class="cabeza">
        <h2><span class="material-symbols-outlined">link</span> Enlaces para cotizar</h2>
        <p class="texto-chico texto-2">Cada proveedor cotiza desde su enlace, sin cuenta. Sirve una sola vez y vence con la fecha límite.</p>
      </div>
      @if (enlaces(); as lista) {
        <ul class="lista">
          @for (e of lista; track e.proveedorId) {
            <li>
              <div class="quien">
                <strong>{{ e.proveedor }}</strong>
                <span class="texto-chico texto-2">
                  @switch (e.estado) {
                    @case ('USADO') { Ya cotizó el {{ e.usadoEn | fecha: 'hora' }} }
                    @case ('VENCIDO') { Venció el {{ e.venceEn | fecha: 'hora' }} }
                    @default { Pendiente · {{ e.correo ? 'enviado a ' + e.correo : 'sin correo: compártalo por WhatsApp' }} }
                  }
                </span>
              </div>
              @if (e.estado === 'PENDIENTE' && e.url) {
                <div class="botones">
                  <button type="button" class="boton chico" (click)="copiar(e)"><span class="material-symbols-outlined">content_copy</span>Copiar</button>
                  <a class="boton chico" target="_blank" rel="noopener" [href]="whatsapp(e)"><span class="material-symbols-outlined">chat</span>WhatsApp</a>
                  @if (!soloLectura()) {
                    <button type="button" class="boton chico" [disabled]="trabajando()" (click)="regenerar(e)">Regenerar</button>
                  }
                </div>
              }
            </li>
          }
        </ul>
      } @else {
        <p class="texto-chico texto-2 vacio">Cargando enlaces…</p>
      }
    </section>
  `,
  styles: `
    .enlaces .cabeza { padding: 16px 16px 8px; }
    .enlaces h2 { display: flex; align-items: center; gap: 6px; margin: 0 0 4px; }
    .lista { list-style: none; margin: 0; padding: 0; }
    .lista li { display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: center; justify-content: space-between;
      padding: 12px 16px; border-top: 1px solid var(--borde, #e5e7eb); }
    .quien { display: flex; flex-direction: column; min-width: 0; }
    .botones { display: flex; flex-wrap: wrap; gap: 6px; }
    .botones .material-symbols-outlined { font-size: 18px; }
    .vacio { padding: 0 16px 16px; }
  `,
})
export class EnlacesRfq implements OnInit {
  private readonly servicio = inject(CotizacionesService);
  private readonly avisos = inject(Avisos);

  /** RFQ abierta. */
  readonly rfqId = input.required<number>();
  /** Consecutivo de la RFQ (para el mensaje de WhatsApp). */
  readonly consecutivo = input('');
  /** Suscripción morosa: no se regenera. */
  readonly soloLectura = input(false);

  protected readonly enlaces = signal<EnlaceCotizacion[] | null>(null);
  protected readonly trabajando = signal(false);

  ngOnInit(): void {
    this.servicio.enlaces(this.rfqId()).subscribe({ next: e => this.enlaces.set(e), error: e => this.avisos.error(e) });
  }

  /** @param e enlace pendiente; lo copia al portapapeles */
  protected copiar(e: EnlaceCotizacion): void {
    navigator.clipboard?.writeText(e.url ?? '').then(
      () => this.avisos.ok(`Enlace de ${e.proveedor} copiado.`),
      () => this.avisos.ok('No se pudo copiar: selecciónelo y cópielo a mano.'),
    );
  }

  /** @param e enlace pendiente; dirección de WhatsApp con el mensaje listo (al teléfono del proveedor, si lo tiene) */
  protected whatsapp(e: EnlaceCotizacion): string {
    const texto = `Hola ${e.proveedor}, le pedimos cotizar la ${this.consecutivo()}. Responda desde este enlace, sin crear cuenta: ${e.url}`;
    const numero = (e.telefono ?? '').replace(/\D/g, '');
    const destino = numero.length === 10 ? '57' + numero : numero; // celular colombiano sin indicativo
    return `https://wa.me/${destino}?text=${encodeURIComponent(texto)}`;
  }

  /** @param e enlace; crea uno nuevo y el anterior deja de servir */
  protected regenerar(e: EnlaceCotizacion): void {
    if (!e.proveedorId || !confirm(`¿Regenerar el enlace de ${e.proveedor}? El anterior dejará de servir.`)) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.regenerarEnlace(this.rfqId(), e.proveedorId).subscribe({
      next: nuevo => {
        this.trabajando.set(false);
        this.enlaces.update(lista => (lista ?? []).map(x => x.proveedorId === nuevo.proveedorId ? nuevo : x));
        this.avisos.ok(`Enlace nuevo para ${e.proveedor}${nuevo.correo ? ', enviado a ' + nuevo.correo : ''}.`);
      },
      error: err => {
        this.trabajando.set(false);
        this.avisos.error(err);
      },
    });
  }
}
