import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Empresas } from './empresas';

/** Detalle que devuelve el alta. */
const creada = { empresa: { id: 9, razonSocial: 'Ferrecentro S.A.S.', codigoPublico: 'ferrecentro' }, modulos: [] };

describe('Consola: alta de empresa con código automático (spec 020)', () => {
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
      razonSocial: 'Ferrecentro S.A.S.', nit: '901223114-5', codigoPublico: '',
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
      razonSocial: 'Ferrecentro S.A.S.', nit: '901223114-5', codigoPublico: '',
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

  it('el formulario ya no pide el código de ingreso', () => {
    expect(raiz().querySelector('#cod')).toBeNull();
    expect(raiz().textContent).toContain('se arma solo con la razón social');
  });

  it('sin código escrito no lo envía y, al crear, muestra el código generado', () => {
    llenar();
    c()['crear']();
    const r = backend.expectOne(q => q.url === '/api/plataforma/empresas' && q.method === 'POST');
    expect(r.request.body.codigoPublico).toBeUndefined();
    r.flush(creada);
    backend.expectOne('/api/plataforma/empresas').flush([]);
    fixture.detectChanges();
    expect(raiz().querySelector('.codigo-creado')?.textContent).toContain('ferrecentro');
    expect([...raiz().querySelectorAll('button')].some(b => b.textContent?.includes('Copiar'))).toBe(true);
  });

  it('"Escribir el código a mano" muestra el campo y envía lo escrito', () => {
    c()['alternarCodigoAMano']();
    fixture.detectChanges();
    expect(raiz().querySelector('#cod')).not.toBeNull();
    llenar();
    c()['nueva'].controls.codigoPublico.setValue('  Ferrecentro Norte ');
    c()['crear']();
    const r = backend.expectOne(q => q.url === '/api/plataforma/empresas' && q.method === 'POST');
    expect(r.request.body.codigoPublico).toBe('Ferrecentro Norte');
    r.flush({ ...creada, empresa: { ...creada.empresa, codigoPublico: 'Ferrecentro Norte' } });
    backend.expectOne('/api/plataforma/empresas').flush([]);
  });

  it('al volver al código automático se borra lo escrito', () => {
    c()['alternarCodigoAMano']();
    c()['nueva'].controls.codigoPublico.setValue('algo');
    c()['alternarCodigoAMano']();
    expect(c()['nueva'].controls.codigoPublico.value).toBe('');
  });
});
