import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { describe, expect, it } from 'vitest';

import { Cifra } from './cifra';

/** Pantalla mínima con dos campos de cifra: uno de pesos con decimales y uno de enteros. */
@Component({
  imports: [FormsModule, Cifra],
  template: `
    <input id="monto" scCifra [ngModel]="monto()" (ngModelChange)="monto.set($event)">
    <input id="entero" scCifra [decimales]="0" [ngModel]="entero()" (ngModelChange)="entero.set($event)">
  `,
})
class Pantalla {
  readonly monto = signal<number | null>(null);
  readonly entero = signal<number | null>(null);
}

describe('Campo de cifra (spec 017)', () => {
  let fixture: ComponentFixture<Pantalla>;
  const campo = (id: string) => (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#' + id)!;
  /** @param id campo @param texto lo que se teclea (reemplaza todo) */
  const escribir = (id: string, texto: string) => {
    const c = campo(id);
    c.value = texto;
    c.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const crear = async () => {
    fixture = TestBed.createComponent(Pantalla);
    fixture.detectChanges();
    await fixture.whenStable();
  };

  it('es un campo de texto con teclado numérico y sin flechas', async () => {
    await crear();
    expect(campo('monto').type).toBe('text');
    expect(campo('monto').getAttribute('inputmode')).toBe('decimal');
  });

  it('pone el punto de miles al escribir y entrega el número limpio', async () => {
    await crear();
    escribir('monto', '1250000');
    expect(campo('monto').value).toBe('1.250.000');
    expect(fixture.componentInstance.monto()).toBe(1250000);
  });

  it('acepta la coma decimal', async () => {
    await crear();
    escribir('monto', '2,5');
    expect(campo('monto').value).toBe('2,5');
    expect(fixture.componentInstance.monto()).toBe(2.5);
    escribir('monto', '1234,567');
    expect(campo('monto').value).toBe('1.234,56');
    expect(fixture.componentInstance.monto()).toBe(1234.56);
  });

  it('vacío entrega null y las letras no entran', async () => {
    await crear();
    escribir('monto', '12a3');
    expect(campo('monto').value).toBe('123');
    escribir('monto', '');
    expect(fixture.componentInstance.monto()).toBeNull();
  });

  it('con decimales=0 no deja coma: lo de después de la coma se descarta (no se pega a la cifra)', async () => {
    await crear();
    escribir('entero', '15,7');
    expect(campo('entero').value).toBe('15');
    expect(fixture.componentInstance.entero()).toBe(15);
    escribir('entero', '2500');
    expect(campo('entero').value).toBe('2.500');
  });

  it('un valor que llega del modelo se pinta con punto de miles', async () => {
    await crear();
    fixture.componentInstance.monto.set(45000.5);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(campo('monto').value).toBe('45.000,5');
  });

  it('al salir quita la coma que quedó colgando', async () => {
    await crear();
    escribir('monto', '12,');
    campo('monto').dispatchEvent(new Event('blur'));
    expect(campo('monto').value).toBe('12');
    expect(fixture.componentInstance.monto()).toBe(12);
  });

  it('lee y muestra cifras sin la pantalla', () => {
    expect(Cifra.leer('1.250.000,75')).toBe(1250000.75);
    expect(Cifra.leer('')).toBeNull();
    expect(Cifra.mostrar(1234)).toBe('1.234');
  });
});
