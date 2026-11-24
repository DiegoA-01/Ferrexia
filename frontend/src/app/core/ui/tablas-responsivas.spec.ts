import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';

import { TablasResponsivas } from './tablas-responsivas';

describe('Tablas responsivas (spec 017)', () => {
  let raiz: HTMLDivElement;

  const crearTabla = () => {
    raiz.innerHTML = `
      <table class="tabla">
        <thead><tr><th>Producto</th><th colspan="2">Cantidad y unidad</th><th></th></tr></thead>
        <tbody>
          <tr><td>Cable</td><td>12</td><td>m</td><td><button>Quitar</button></td></tr>
          <tr><td colspan="4" class="vacio">Sin más</td></tr>
        </tbody>
      </table>`;
    return raiz.querySelector('table')!;
  };

  afterEach(() => raiz?.remove());

  it('pone en cada celda el encabezado de su columna, también con colspan', () => {
    raiz = document.createElement('div');
    document.body.appendChild(raiz);
    const tabla = crearTabla();
    TestBed.inject(TablasResponsivas).etiquetar(raiz);
    const celdas = [...tabla.querySelectorAll('tbody tr:first-child td')].map(td => td.getAttribute('data-etiqueta'));
    expect(celdas).toEqual(['Producto', 'Cantidad y unidad', 'Cantidad y unidad', '']);
    // una celda que ocupa toda la fila no lleva etiqueta
    expect(tabla.querySelector('td.vacio')!.getAttribute('data-etiqueta')).toBe('');
  });

  it('las tablas que aparecen después también reciben la etiqueta', async () => {
    raiz = document.createElement('div');
    document.body.appendChild(raiz);
    TestBed.inject(TablasResponsivas).iniciar(raiz);
    const tabla = crearTabla();
    await new Promise(r => setTimeout(r, 50));
    expect(tabla.querySelector('tbody td')!.getAttribute('data-etiqueta')).toBe('Producto');
  });
});
