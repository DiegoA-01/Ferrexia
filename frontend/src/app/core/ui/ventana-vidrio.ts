import { Component, ElementRef, effect, input, output, viewChild } from '@angular/core';

/** Contador para que cada ventana tenga un id de título propio. */
let siguiente = 0;

/**
 * Estilo de las mini tarjetas de totales (`<dl class="totales">`) que se usan dentro de la ventana de vidrio. Cada
 * pantalla lo suma a sus `styles` porque el contenido proyectado lleva los estilos de la pantalla que lo escribe.
 */
export const ESTILO_TOTALES = `
  .totales { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 10px; margin: 0;
    > div { padding: 10px 12px; border-radius: 14px; background: rgba(255, 255, 255, .6); border: 1px solid rgba(255, 255, 255, .8);
      box-shadow: 0 1px 3px rgba(15, 23, 42, .08); }
    dt { font-size: 12px; color: var(--sc-texto-2); } dd { margin: 2px 0 0; font-weight: 700; font-size: 15px; } }
`;

/**
 * Ventana flotante centrada con efecto vidrio (fondo borroso, tarjeta translúcida con brillo y sombra). Se cierra con la
 * X, con Escape y con un clic en el fondo; el foco entra a la ventana al abrirse. El contenido va dentro de la etiqueta;
 * `[etiqueta]` se pone junto al título y `[acciones]` junto a la X del encabezado.
 *
 * `bloquearCierre` impide cerrar con el fondo (hay un formulario a medio llenar) y `bloquearEscape`, con Escape (hay un
 * envío en curso). La X siempre emite `cerrar`: quien la usa decide qué hacer.
 */
@Component({
  selector: 'sc-ventana-vidrio',
  host: { '(document:keydown.escape)': 'alEscape()' },
  template: `
    @if (abierta()) {
      <div class="velo-vidrio" (click)="clicFondo($event)">
        <section #tarjeta class="tarjeta modal-vidrio" role="dialog" aria-modal="true" [attr.aria-labelledby]="idTitulo()" tabindex="-1">
          <div class="enc-vidrio">
            <h2 [id]="idTitulo()">{{ titulo() }} <ng-content select="[etiqueta]" /></h2>
            <div class="fila acciones-vidrio">
              <ng-content select="[acciones]" />
              <button type="button" class="boton chico" aria-label="Cerrar la ventana" (click)="cerrar.emit()">
                <span class="material-symbols-outlined" aria-hidden="true">close</span></button>
            </div>
          </div>
          <ng-content />
        </section>
      </div>
    }
  `,
  styles: `
    .velo-vidrio { position: fixed; inset: 0; z-index: 30; display: flex; align-items: center; justify-content: center; padding: 16px; overflow-y: auto;
      background: rgba(15, 23, 42, .5); }
    @supports (backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px)) {
      .velo-vidrio { background: rgba(15, 23, 42, .35); -webkit-backdrop-filter: blur(6px); backdrop-filter: blur(6px); }
    }
    .modal-vidrio { display: grid; gap: 12px; width: fit-content; min-width: min(420px, 100%); max-width: min(860px, 100%); max-height: 100%; overflow-y: auto; margin: auto; outline: none;
      padding: 20px 22px; border-radius: 24px; color: var(--sc-texto); border: 1px solid rgba(255, 255, 255, .55);
      background: rgba(255, 255, 255, .97);
      box-shadow: 0 24px 60px rgba(15, 23, 42, .28), 0 4px 14px rgba(15, 23, 42, .12), inset 0 1px 0 rgba(255, 255, 255, .9);
      animation: abre-vidrio .16s ease-out; }
    @supports (backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px)) {
      .modal-vidrio { background: linear-gradient(180deg, rgba(255, 255, 255, .88), rgba(255, 255, 255, .74));
        -webkit-backdrop-filter: blur(22px) saturate(1.6); backdrop-filter: blur(22px) saturate(1.6); }
    }
    @keyframes abre-vidrio { from { opacity: 0; transform: scale(.97); } to { opacity: 1; transform: scale(1); } }
    @media (prefers-reduced-motion: reduce) { .modal-vidrio { animation: none; } }
    @media (max-width: 600px) { .velo-vidrio { padding: 12px; } .modal-vidrio { padding: 16px; border-radius: 20px; } }
    .enc-vidrio { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px 16px;
      h2 { display: flex; align-items: center; gap: 10px; font-size: 22px; margin: 0; } }
    .acciones-vidrio { flex-wrap: wrap; gap: 8px; }
  `,
})
export class VentanaVidrio {
  /** Texto del encabezado. */
  readonly titulo = input.required<string>();
  /** Si la ventana está a la vista. */
  readonly abierta = input(false);
  /** Impide cerrar con un clic en el fondo (formulario a medio llenar o envío en curso). */
  readonly bloquearCierre = input(false);
  /** Impide cerrar con Escape (envío en curso). */
  readonly bloquearEscape = input(false);
  /** Id del título, para `aria-labelledby`. */
  readonly idTitulo = input(`t-vidrio-${++siguiente}`);
  /** Se pidió cerrar (X, Escape o fondo). */
  readonly cerrar = output<void>();

  private readonly tarjeta = viewChild<ElementRef<HTMLElement>>('tarjeta');

  constructor() {
    // al abrir, el foco entra a la ventana
    effect(() => {
      if (this.abierta() && this.tarjeta()) {
        const t = this.tarjeta()!.nativeElement;
        setTimeout(() => t.focus());
      }
    });
  }

  /** Escape cierra la ventana abierta salvo que haya un envío en curso. */
  protected alEscape(): void {
    if (this.abierta() && !this.bloquearEscape()) {
      this.cerrar.emit();
    }
  }

  /** @param e clic en el fondo: cierra solo si cayó fuera de la tarjeta y no está bloqueado */
  protected clicFondo(e: Event): void {
    if (e.target === e.currentTarget && !this.bloquearCierre()) {
      this.cerrar.emit();
    }
  }
}
