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
    backend.expectOne('/api/plataforma/planes').flush([{ id: 1, codigo: 'BASICO', nombre: 'Básico', activo: true }]);
    c()['creando'].set(true);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  /** Llena el formulario con datos válidos, sin código. */
  const llenar = () => {
    c()['nueva'].setValue({
      razonSocial: 'Ferrecentro S.A.S.', nit: '901223114-5', codigoPublico: '', planCodigo: 'BASICO',
      estadoInicial: 'PRUEBA', adminNombre: 'Laura Gómez', adminCorreo: 'laura@ferrecentro.co', adminPassword: 'Clave12345',
    });
  };

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
