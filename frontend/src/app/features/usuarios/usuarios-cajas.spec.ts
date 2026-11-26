import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { Usuarios } from './usuarios';

/** Spec 025: la ficha del trabajador muestra sus cajas asignadas, solo para consultar. */
describe('Ficha del trabajador: cajas asignadas', () => {
  let backend: HttpTestingController;

  /** @param conCaja si la empresa tiene el módulo de caja y quien edita ve las cajas */
  const crear = (conCaja: boolean) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: { tieneRol: () => true, tieneModulo: () => conCaja, tienePermiso: () => conCaja,
          soloLectura: () => false, empresa: signal(null), usuario: signal(null) } }],
    });
    backend = TestBed.inject(HttpTestingController);
    // sin detectChanges: no se carga la lista, solo se prueba la ficha
    return TestBed.createComponent(Usuarios).componentInstance as unknown as Record<string, any>;
  };

  afterEach(() => backend.verify());

  it('al editar pide sus cajas y las muestra por nombre', () => {
    const c = crear(true);
    c['editar']({ id: 5, nombreCompleto: 'Pedro Cajero', correo: 'p@x.co', roles: ['CAJERO'] });
    backend.expectOne('/api/cajas/de-usuario/5').flush([{ cajaId: 1, nombre: 'Caja 1' }, { cajaId: 2, nombre: 'Caja 2' }]);
    expect(c['cajasDelUsuario']()).toEqual(['Caja 1', 'Caja 2']);
  });

  it('sin cajas asignadas queda la lista vacía (puede usar todas)', () => {
    const c = crear(true);
    c['editar']({ id: 6, nombreCompleto: 'Ana', correo: 'a@x.co', roles: ['CAJERO'] });
    backend.expectOne('/api/cajas/de-usuario/6').flush([]);
    expect(c['cajasDelUsuario']()).toEqual([]);
  });

  it('sin el módulo de caja no las pide', () => {
    const c = crear(false);
    c['editar']({ id: 7, nombreCompleto: 'Luis', correo: 'l@x.co', roles: ['SOLICITANTE'] });
    backend.expectNone(r => r.url.startsWith('/api/cajas'));
    expect(c['cajasDelUsuario']()).toBeNull();
  });
});
