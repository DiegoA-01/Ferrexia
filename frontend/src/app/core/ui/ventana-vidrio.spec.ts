import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { VentanaVidrio } from './ventana-vidrio';

@Component({
  imports: [VentanaVidrio],
  template: `
    <sc-ventana-vidrio titulo="Venta C1-000001" [abierta]="abierta()" [bloquearCierre]="bloqueo()" (cerrar)="cierres.set(cierres() + 1)">
      <button acciones type="button" id="accion">Acción</button>
      <p id="contenido">Cuerpo</p>
    </sc-ventana-vidrio>`,
})
class Anfitrion {
  readonly abierta = signal(true);
  readonly bloqueo = signal(false);
  readonly cierres = signal(0);
}

describe('Ventana de vidrio', () => {
  let fixture: ComponentFixture<Anfitrion>;
  const raiz = () => fixture.nativeElement as HTMLElement;
  const crear = () => {
    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
  };
  const actualizar = () => fixture.detectChanges();

  it('cerrada no pinta nada y abierta muestra el diálogo con su título, acciones y contenido', () => {
    crear();
    fixture.componentInstance.abierta.set(false);
    actualizar();
    expect(raiz().querySelector('[role="dialog"]')).toBeNull();
    fixture.componentInstance.abierta.set(true);
    actualizar();
    const d = raiz().querySelector('[role="dialog"]')!;
    expect(d.getAttribute('aria-modal')).toBe('true');
    expect(d.getAttribute('aria-labelledby')).toBe(d.querySelector('h2')!.id);
    expect(d.querySelector('h2')!.textContent).toContain('Venta C1-000001');
    expect(d.querySelector('#accion')).not.toBeNull();
    expect(d.querySelector('#contenido')!.textContent).toBe('Cuerpo');
  });

  it('la X pide cerrar', () => {
    crear();
    raiz().querySelector<HTMLButtonElement>('button[aria-label="Cerrar la ventana"]')!.click();
    expect(fixture.componentInstance.cierres()).toBe(1);
  });

  it('Escape pide cerrar solo si está abierta', () => {
    crear();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(fixture.componentInstance.cierres()).toBe(1);
    fixture.componentInstance.abierta.set(false);
    actualizar();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(fixture.componentInstance.cierres()).toBe(1);
  });

  it('el clic en el fondo cierra, el clic dentro de la tarjeta no', () => {
    crear();
    raiz().querySelector<HTMLElement>('.modal-vidrio')!.click();
    expect(fixture.componentInstance.cierres()).toBe(0);
    raiz().querySelector<HTMLElement>('.velo-vidrio')!.click();
    expect(fixture.componentInstance.cierres()).toBe(1);
  });

  it('con bloquearCierre el fondo no cierra, pero la X sí', () => {
    crear();
    fixture.componentInstance.bloqueo.set(true);
    actualizar();
    raiz().querySelector<HTMLElement>('.velo-vidrio')!.click();
    expect(fixture.componentInstance.cierres()).toBe(0);
    raiz().querySelector<HTMLButtonElement>('button[aria-label="Cerrar la ventana"]')!.click();
    expect(fixture.componentInstance.cierres()).toBe(1);
  });
});
