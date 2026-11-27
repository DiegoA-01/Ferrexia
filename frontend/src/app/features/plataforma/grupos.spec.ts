import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Grupos } from './grupos';

const empresas = [
  { id: 1, razonSocial: 'Ferretería Norte', estado: 'ACTIVA' },
  { id: 2, razonSocial: 'Eléctricos Sur', estado: 'ACTIVA' },
  { id: 3, razonSocial: 'Sin grupo S.A.S.', estado: 'PRUEBA' },
];
const grupo = {
  id: 10, nombre: 'Grupo Norte', activo: true,
  empresas: [{ id: 1, razonSocial: 'Ferretería Norte', codigoPublico: 'norte' }],
  superAdmins: [{ id: 50, nombre: 'Dueña', correo: 'dueno@x.co', activo: true, mfaActivo: false }],
};

describe('Consola: Grupos de empresas (spec 035)', () => {
  let fixture: ComponentFixture<Grupos>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const html = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Grupos);
    fixture.detectChanges();
    backend.expectOne('/api/plataforma/empresas').flush(empresas);
    backend.expectOne('/api/plataforma/grupos').flush([grupo]);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  /** Responde la recarga que sigue a cada acción. */
  const recargar = (g: unknown = grupo) => backend.expectOne('/api/plataforma/grupos').flush([g]);

  it('lista los grupos con sus empresas y super administradores', () => {
    expect(html().textContent).toContain('Grupo Norte');
    expect(html().textContent).toContain('Ferretería Norte');
    expect(html().textContent).toContain('dueno@x.co');
    expect(html().textContent).toContain('Falta configurar el segundo factor');
  });

  it('crea un grupo con su nombre y recarga la lista', () => {
    c()['nombreGrupo'] = 'Grupo Sur';
    c()['crearGrupo']();
    const r = backend.expectOne(q => q.url === '/api/plataforma/grupos' && q.method === 'POST');
    expect(r.request.body).toEqual({ nombre: 'Grupo Sur' });
    r.flush({ id: 11, nombre: 'Grupo Sur' });
    recargar();
    expect(c()['nombreGrupo']).toBe('');
  });

  it('el selector ofrece solo las empresas que no están en el grupo y asigna la elegida', () => {
    expect(c()['disponibles'](grupo).map((e: any) => e.id)).toEqual([2, 3]);
    c()['elegir'](10, 3);
    c()['asignar'](10);
    const r = backend.expectOne('/api/plataforma/empresas/3/grupo');
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ grupoId: 10 });
    r.flush({ empresaId: 3, grupoId: 10 });
    recargar();
  });

  it('quitar una empresa manda grupoId nulo', () => {
    c()['quitar'](1);
    const r = backend.expectOne('/api/plataforma/empresas/1/grupo');
    expect(r.request.body).toEqual({ grupoId: null });
    r.flush({ empresaId: 1 });
    recargar({ ...grupo, empresas: [] });
  });

  it('muestra legible el 409 TRASLADOS_EN_TRANSITO al sacar una empresa', () => {
    c()['quitar'](1);
    backend.expectOne('/api/plataforma/empresas/1/grupo').flush(
      { codigo: 'TRASLADOS_EN_TRANSITO', mensaje: 'La empresa tiene traslados en tránsito como origen o destino: recíbalos o anúlelos antes de sacarla del grupo' },
      { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(html().querySelector('.aviso.rojo')?.textContent).toContain('traslados en tránsito');
    backend.expectNone('/api/plataforma/grupos');
  });

  it('crea al super administrador solo con una clave fuerte de 12 o más caracteres con letra y número', () => {
    c()['abrirSuper'](10);
    fixture.detectChanges();
    c()['superForm'].setValue({ nombre: 'Nuevo', correo: 'nuevo@x.co', password: 'corta1' });
    expect(c()['superForm'].invalid).toBe(true);
    c()['superForm'].setValue({ nombre: 'Nuevo', correo: 'nuevo@x.co', password: 'soloLetrasLargas' });
    expect(c()['superForm'].invalid).toBe(true);
    c()['superForm'].setValue({ nombre: 'Nuevo', correo: 'nuevo@x.co', password: 'ClaveFuerte123' });
    expect(c()['superForm'].valid).toBe(true);
    c()['crearSuper'](10);
    const r = backend.expectOne('/api/plataforma/grupos/10/super-admin');
    expect(r.request.body).toEqual({ nombre: 'Nuevo', correo: 'nuevo@x.co', password: 'ClaveFuerte123' });
    r.flush({ id: 51, nombre: 'Nuevo', correo: 'nuevo@x.co', activo: true, mfaActivo: false });
    recargar();
    expect(c()['formSuper']()).toBeNull();
  });

  it('activa o desactiva y reinicia el segundo factor del super administrador', () => {
    const s = grupo.superAdmins[0];
    c()['cambiarEstado'](grupo, s);
    const e = backend.expectOne('/api/plataforma/grupos/10/super-admin/50');
    expect(e.request.method).toBe('PATCH');
    expect(e.request.body).toEqual({ activo: false });
    e.flush({ ...s, activo: false });
    recargar();

    c()['reiniciarMfa'](grupo, s);
    const m = backend.expectOne('/api/plataforma/grupos/10/super-admin/50/reiniciar-mfa');
    expect(m.request.method).toBe('POST');
    m.flush(s);
    recargar();
  });
});
