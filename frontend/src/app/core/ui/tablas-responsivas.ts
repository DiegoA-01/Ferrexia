import { DestroyRef, Injectable, inject } from '@angular/core';

/**
 * Tablas responsivas (spec 017). En pantallas de menos de 768 px, {@code styles.scss} muestra cada fila de una
 * {@code table.tabla} como una tarjeta y cada celda con su encabezado al lado. Ese encabezado sale del atributo
 * {@code data-etiqueta}, que este servicio copia solo desde el {@code <thead>} a cada celda: así ninguna pantalla tiene
 * que marcar sus tablas a mano, y las tablas que aparecen después (al buscar, al cambiar de página) también lo reciben.
 */
@Injectable({ providedIn: 'root' })
export class TablasResponsivas {
  private observador: MutationObserver | null = null;
  private pendiente = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.observador?.disconnect());
  }

  /** @param raiz desde dónde observar (por defecto, toda la página) */
  iniciar(raiz: HTMLElement = document.body): void {
    if (this.observador || typeof MutationObserver === 'undefined') {
      return;
    }
    this.etiquetar(raiz);
    this.observador = new MutationObserver(() => this.programar(raiz));
    this.observador.observe(raiz, { childList: true, subtree: true, characterData: true });
  }

  /** Agrupa los cambios del DOM en una sola pasada por cuadro de pantalla. */
  private programar(raiz: HTMLElement): void {
    if (this.pendiente) {
      return;
    }
    this.pendiente = true;
    const correr = () => {
      this.pendiente = false;
      this.etiquetar(raiz);
    };
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(correr);
    } else {
      setTimeout(correr);
    }
  }

  /**
   * Pone en cada celda el texto del encabezado de su columna (tiene en cuenta {@code colspan}).
   *
   * @param raiz donde buscar las tablas
   */
  etiquetar(raiz: ParentNode): void {
    raiz.querySelectorAll<HTMLTableElement>('table.tabla').forEach(tabla => {
      const encabezados: string[] = [];
      tabla.querySelectorAll<HTMLTableCellElement>('thead tr:first-child th').forEach(th => {
        const texto = (th.textContent ?? '').replace(/\s+/g, ' ').trim();
        for (let i = 0; i < Math.max(1, th.colSpan); i++) {
          encabezados.push(texto);
        }
      });
      tabla.querySelectorAll<HTMLTableRowElement>('tbody tr, tfoot tr').forEach(fila => {
        let columna = 0;
        for (const celda of Array.from(fila.cells)) {
          const etiqueta = celda.colSpan > 1 ? '' : encabezados[columna] ?? '';
          if (celda.getAttribute('data-etiqueta') !== etiqueta) {
            celda.setAttribute('data-etiqueta', etiqueta);
          }
          columna += Math.max(1, celda.colSpan);
        }
      });
    });
  }
}
