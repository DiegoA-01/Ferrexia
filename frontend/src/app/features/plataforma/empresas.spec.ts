import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Empresas } from './empresas';

/** Detalle que devuelve el alta. */
const creada = { empresa: { id: 9, razonSocial: 'Ferrecentro S.A.S.' }, modulos: [] };

describe('Consola: alta de empresa sin código de ingreso (spec 037)', () => {
  let fixture: ComponentFixture<Empresas>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const raiz = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Empresas);
    fixture.detectChanges();
    backend.expectOne('/api/plataforma/empresas').flush([]);
    backend.expectOne('/api/plataforma/modulos').flush([
      { codigo: 'COMPRAS', nombre: 'Compras', descripcion: 'Solicitudes y órdenes' },
      { codigo: 'INVENTARIO', nombre: 'Inventario' }, { codigo: 'VENTAS', nombre: 'Ventas' },
    ]);
    c()['creando'].set(true);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  /** Llena el formulario con datos válidos, sin código, y marca Ventas. */
  const llenar = () => {
    c()['nueva'].setValue({
      razonSocial: 'Ferrecentro S.A.S.', nit: '901223114-5',
      estadoInicial: 'PRUEBA', adminNombre: 'Laura Gómez', adminCorreo: 'laura@ferrecentro.co', adminPassword: 'Clave12345',
    });
    c()['marcarModulo']('VENTAS');
  };

  it('spec 024: no pide plan; muestra el panel de módulos y envía solo los marcados', () => {
    expect(raiz().querySelector('#plan')).toBeNull();
    expect(raiz().textContent).toContain('Módulos que va a usar');
    const casillas = raiz().querySelectorAll<HTMLInputElement>('.opcion-modulo input[type=checkbox]');
    expect(casillas.length).toBe(3);
    expect([...casillas].every(x => !x.checked)).toBe(true);
    llenar();
    casillas[0].click(); // Compras
    fixture.detectChanges();
    expect(raiz().textContent).toContain('2 de 3 marcados');
    c()['crear']();
    const r = backend.expectOne(q => q.url === '/api/plataforma/empresas' && q.method === 'POST');
    expect([...r.request.body.modulos].sort()).toEqual(['COMPRAS', 'VENTAS']);
    expect(r.request.body.planCodigo).toBeUndefined();
    r.flush(creada);
    backend.expectOne('/api/plataforma/empresas').flush([]);
    expect(c()['modulosAlta']()).toEqual([]);
  });

  it('sin módulos marcados no se crea la empresa; "Marcar todos" los marca', () => {
    c()['nueva'].setValue({
      razonSocial: 'Ferrecentro S.A.S.', nit: '901223114-5',
      estadoInicial: 'PRUEBA', adminNombre: 'Laura Gómez', adminCorreo: 'laura@ferrecentro.co', adminPassword: 'Clave12345',
    });
    c()['crear']();
    backend.expectNone(q => q.method === 'POST');
    c()['marcarTodos'](true);
    expect(c()['modulosAlta']()).toEqual(['COMPRAS', 'INVENTARIO', 'VENTAS']);
    c()['marcarTodos'](false);
    expect(c()['modulosAlta']()).toEqual([]);
  });

  it('spec 036: con datos malos dice qué corregir, campo por campo y en una lista', () => {
    c()['nueva'].patchValue({ razonSocial: 'Ferrecentro', nit: '9012', adminNombre: 'Laura', adminCorreo: 'laura@x.co', adminPassword: '123456' });
    c()['crear']();
    fixture.detectChanges();
    backend.expectNone(q => q.method === 'POST');
    const aviso = raiz().querySelector('[role=alert]')?.textContent ?? '';
    expect(aviso).toContain('Contraseña inicial: mínimo 8 caracteres, con al menos una letra y un número');
    expect(aviso).toContain('Módulos: marca al menos uno');
    expect(aviso).not.toContain('Razón social');
    expect(raiz().querySelector('#ap')?.closest('.campo')?.querySelector('.error')?.textContent).toContain('mínimo 8 caracteres');
    // el botón no queda apagado: oprimirlo es lo que muestra el aviso
    expect(raiz().querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(false);
  });

  it('el formulario no pide ni muestra ningún código de ingreso', () => {
    expect(raiz().querySelector('#cod')).toBeNull();
    expect(raiz().textContent).not.toContain('ódigo de ingreso');
    expect(raiz().textContent).not.toContain('ódigo de empresa');
  });

  it('al crear no envía código y no muestra ningún aviso con el código', () => {
    llenar();
    c()['crear']();
    const r = backend.expectOne(q => q.url === '/api/plataforma/empresas' && q.method === 'POST');
    expect(r.request.body.codigoPublico).toBeUndefined();
    r.flush(creada);
    backend.expectOne('/api/plataforma/empresas').flush([]);
    fixture.detectChanges();
    expect(raiz().querySelector('.codigo-creado')).toBeNull();
  });
});
