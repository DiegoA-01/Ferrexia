import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, OnDestroy, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Cotizacion, CotizacionPublica } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { CotizarEnlaceService } from '../../core/servicios/cotizar-enlace.service';
import { iso } from '../../core/servicios/reportes.service';
import { Avisos } from '../../core/ui/avisos';
import { Cifra } from '../../core/ui/cifra';

/** Precio e impuesto que el proveedor escribe por línea. */
interface Oferta {
  precio: number | null;
  impuesto: number;
}

/** API mínima del widget de Cloudflare Turnstile. */
interface TurnstileApi {
  render(elemento: HTMLElement, opciones: Record<string, unknown>): string;
  reset(id?: string): void;
  remove(id?: string): void;
}

const SCRIPT_TURNSTILE = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';

/**
 * Spec 031 · El proveedor cotiza desde su enlace, sin cuenta. El total lo calcula el backend al guardar; al enviar, el
 * enlace queda cerrado. Lleva verificación anti-bot (Turnstile) y un campo trampa oculto.
 */
@Component({
  selector: 'sc-cotizar-enlace',
  imports: [Cifra, FormsModule, PesosPipe, NumeroPipe, FechaPipe],
  templateUrl: './cotizar-enlace.html',
  styleUrl: './cotizar-enlace.scss',
})
export class CotizarEnlace implements OnInit, OnDestroy {
  private readonly servicio = inject(CotizarEnlaceService);
  private readonly avisos = inject(Avisos);

  /** Texto del enlace (de la ruta). */
  readonly token = input.required<string>();

  private readonly cajaTurnstile = viewChild<ElementRef<HTMLElement>>('turnstile');
  private widgetId: string | null = null;

  protected readonly datos = signal<CotizacionPublica | null>(null);
  /** El enlace no sirve (vencido, usado o inventado). */
  protected readonly noValido = signal(false);
  /** Se envió en esta visita. */
  protected readonly enviada = signal(false);
  protected readonly cotizacion = signal<Cotizacion | null>(null);
  protected readonly ofertas = signal<Record<number, Oferta>>({});
  protected readonly plazo = signal<number | null>(null);
  protected readonly garantia = signal<number | null>(null);
  protected readonly validez = signal(iso(new Date(Date.now() + 15 * 86_400_000)));
  protected readonly condiciones = signal('A 30 días');
  protected readonly notas = signal('');
  /** Campo trampa: las personas no lo ven; si llega con valor, lo llenó un bot. */
  protected readonly sitioWeb = signal('');
  protected readonly trabajando = signal(false);
  protected readonly sinGuardar = signal(false);
  protected readonly nombreArchivo = signal<string | null>(null);
  /** Token del widget anti-bot. */
  protected readonly tokenTurnstile = signal<string | null>(null);

  protected readonly tieneArchivo = computed(() => !!this.cotizacion()?.documentoOriginalId);
  protected readonly conAntiBot = computed(() => !!this.datos()?.turnstileClaveSitio);
  protected readonly puedeEnviar = computed(() => !this.trabajando() && (!this.conAntiBot() || !!this.tokenTurnstile()));

  ngOnInit(): void {
    this.servicio.ver(this.token()).subscribe({
      next: d => {
        this.datos.set(d);
        this.aplicar(d.cotizacion ?? null);
        if (d.turnstileClaveSitio) {
          setTimeout(() => this.montarTurnstile(d.turnstileClaveSitio!));
        }
      },
      error: (e: HttpErrorResponse) => e.status === 404 ? this.noValido.set(true) : this.avisos.error(e),
    });
  }

  ngOnDestroy(): void {
    const api = (window as unknown as { turnstile?: TurnstileApi }).turnstile;
    if (api && this.widgetId) {
      api.remove(this.widgetId);
    }
  }

  /** @param lineaId línea de la RFQ */
  protected oferta(lineaId: number | undefined): Oferta {
    return this.ofertas()[lineaId ?? -1] ?? { precio: null, impuesto: 19 };
  }

  /** @param lineaId línea; @param campo dato; @param valor valor escrito */
  protected cambiar(lineaId: number | undefined, campo: keyof Oferta, valor: number | null): void {
    if (lineaId === undefined) {
      return;
    }
    this.ofertas.update(o => ({ ...o, [lineaId]: { ...this.oferta(lineaId), [campo]: valor } }));
    this.sinGuardar.set(true);
  }

  /** @param luegoEnviar guarda el borrador y lo envía */
  protected guardar(luegoEnviar = false): void {
    const rfq = this.datos()?.rfq;
    if (!rfq) {
      return;
    }
    const lineas = (rfq.lineas ?? []).map(l => ({ l, o: this.oferta(l.id) })).filter(({ o }) => (o.precio ?? 0) > 0)
      .map(({ l, o }) => ({
        rfqLineaId: l.id!, cantidadOfertada: l.cantidad ?? 0, unidadOfertada: l.unidadCodigo ?? '', factorConversion: 1,
        precioUnitarioOfertado: o.precio ?? 0, impuestoPct: o.impuesto,
      }));
    if (!lineas.length) {
      if (this.tieneArchivo()) {
        if (luegoEnviar) {
          this.enviar();
        } else {
          this.avisos.ok('Su archivo ya quedó guardado. Puede enviar la cotización sin escribir precios.');
        }
        return;
      }
      this.avisos.ok('Escriba el precio de al menos un producto o suba el archivo de su cotización.');
      return;
    }
    this.trabajando.set(true);
    const condiciones = [this.condiciones().trim(), this.notas().trim()].filter(Boolean).join('. ');
    this.servicio.guardar(this.token(), {
      moneda: 'COP', plazoEntregaDias: this.plazo() ?? undefined, garantiaMeses: this.garantia() ?? undefined,
      validezHasta: this.validez() || undefined, condicionesComerciales: condiciones, lineas,
    }).subscribe({
      next: c => {
        this.aplicar(c);
        this.sinGuardar.set(false);
        if (luegoEnviar) {
          this.enviar();
        } else {
          this.trabajando.set(false);
          this.avisos.ok('Borrador guardado. El total ya está calculado.');
        }
      },
      error: e => this.fallo(e),
    });
  }

  /** @param evento cambio del selector de archivos */
  protected subirArchivo(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];
    campo.value = '';
    if (!archivo) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.subirArchivo(this.token(), archivo).subscribe({
      next: c => {
        this.trabajando.set(false);
        this.aplicar(c);
        this.nombreArchivo.set(archivo.name);
        this.avisos.ok(`Archivo «${archivo.name}» adjunto. Ya puede enviar la cotización.`);
      },
      error: e => this.fallo(e),
    });
  }

  private enviar(): void {
    this.trabajando.set(true);
    this.servicio.enviar(this.token(), this.tokenTurnstile(), this.sitioWeb()).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.enviada.set(true);
      },
      error: e => {
        this.fallo(e);
        // el token del widget sirve una sola vez: se pide otro
        const api = (window as unknown as { turnstile?: TurnstileApi }).turnstile;
        this.tokenTurnstile.set(null);
        if (api && this.widgetId) {
          api.reset(this.widgetId);
        }
      },
    });
  }

  /** @param e error del backend; un 404 quiere decir que el enlace dejó de servir */
  private fallo(e: unknown): void {
    this.trabajando.set(false);
    if (e instanceof HttpErrorResponse && e.status === 404) {
      this.noValido.set(true);
      return;
    }
    this.avisos.error(e);
  }

  private aplicar(c: Cotizacion | null): void {
    this.cotizacion.set(c);
    if (!c) {
      return;
    }
    this.plazo.set(c.plazoEntregaDias ?? null);
    this.garantia.set(c.garantiaMeses ?? null);
    if (c.validezHasta) {
      this.validez.set(c.validezHasta);
    }
    if (c.condicionesComerciales) {
      this.condiciones.set(c.condicionesComerciales);
    }
    const o: Record<number, Oferta> = {};
    for (const l of c.lineas ?? []) {
      if (l.rfqLineaId) {
        o[l.rfqLineaId] = { precio: l.precioUnitarioOfertado ?? null, impuesto: l.impuestoPct ?? 19 };
      }
    }
    this.ofertas.set(o);
  }

  /** @param claveSitio clave pública; carga el script de Cloudflare una vez y dibuja el widget */
  private montarTurnstile(claveSitio: string): void {
    const dibujar = () => {
      const api = (window as unknown as { turnstile?: TurnstileApi }).turnstile;
      const caja = this.cajaTurnstile()?.nativeElement;
      if (!api || !caja || this.widgetId) {
        return;
      }
      this.widgetId = api.render(caja, {
        sitekey: claveSitio,
        language: 'es',
        callback: (t: string) => this.tokenTurnstile.set(t),
        'expired-callback': () => this.tokenTurnstile.set(null),
        'error-callback': () => this.tokenTurnstile.set(null),
      });
    };
    if ((window as unknown as { turnstile?: TurnstileApi }).turnstile) {
      dibujar();
      return;
    }
    let script = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_TURNSTILE}"]`);
    if (!script) {
      script = document.createElement('script');
      script.src = SCRIPT_TURNSTILE;
      script.async = true;
      document.head.appendChild(script);
    }
    script.addEventListener('load', dibujar);
  }
}
