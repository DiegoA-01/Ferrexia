import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { App } from './app';

describe('Raíz de la aplicación (spec 017)', () => {
  afterEach(() => document.querySelectorAll('input[data-prueba]').forEach(i => i.remove()));

  it('la rueda del mouse sobre un campo de número enfocado no cambia su valor: el campo pierde el foco', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const campo = document.createElement('input');
    campo.type = 'number';
    campo.setAttribute('data-prueba', '');
    document.body.appendChild(campo);
    campo.focus();
    expect(document.activeElement).toBe(campo);
    campo.dispatchEvent(new WheelEvent('wheel', { deltaY: 100, bubbles: true }));
    expect(document.activeElement).not.toBe(campo);
  });
});
