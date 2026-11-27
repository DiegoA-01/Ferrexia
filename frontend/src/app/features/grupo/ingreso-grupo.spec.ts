import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { authInterceptor } from '../../core/http/auth.interceptor';
import { GrupoSesionService } from '../../core/sesion/grupo-sesion.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { IngresoGrupo } from './ingreso-grupo';

const sesionGrupo = { token: 'jwt-grupo', tipo: 'Bearer', expiraEnSegundos: 900, usuarioId: 4, correo: 'dueno@x.co', nombre: 'Dueña', grupoId: 2, grupoNombre: 'Grupo Norte' };

describe('Ingreso del grupo con segundo factor (spec 035)', () => {
  let fixture: ComponentFixture<IngresoGrupo>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const html = () => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(IngresoGrupo);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('primer ingreso: pide la clave sin token, muestra el secreto para configurar y luego el código abre la sesión', async () => {
    c()['correo'] = 'dueno@x.co';
    c()['clave'] = 'ClaveFuerte123';
    c()['ingresar']();
    const login = backend.expectOne('/api/grupo/auth/login');
    expect(login.request.headers.has('Authorization')).toBe(false);
    expect(login.request.body).toEqual({ correo: 'dueno@x.co', password: 'ClaveFuerte123' });
    login.flush({ desafio: 'd-1', mfaConfigurado: false, secretoMfa: 'ABCD EFGH' });
    fixture.detectChanges();
    expect(html().textContent).toContain('Configura tu app autenticadora');
    expect(html().textContent).toContain('ABCD EFGH');

    c()['codigo'] = '123456';
    c()['confirmar']();
    const mfa = backend.expectOne('/api/grupo/auth/mfa');
    expect(mfa.request.headers.has('Authorization')).toBe(false);
    expect(mfa.request.body).toEqual({ desafio: 'd-1', codigo: '123456' });
    mfa.flush(sesionGrupo);

    const grupo = TestBed.inject(GrupoSesionService);
    expect(grupo.autenticado()).toBe(true);
    expect(grupo.grupoNombre()).toBe('Grupo Norte');
    expect(sessionStorage.getItem('sc.grupo')).toContain('jwt-grupo');
    await fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/grupo');
  });

  it('ingreso posterior: no muestra el secreto, solo pide el código', () => {
    c()['ingresar']();
    backend.expectOne('/api/grupo/auth/login').flush({ desafio: 'd-2', mfaConfigurado: true });
    fixture.detectChanges();
    expect(html().textContent).not.toContain('Configura tu app autenticadora');
    expect(html().querySelector('#codigo')).not.toBeNull();
  });

  it('un código errado muestra el mensaje del backend y no abre sesión', () => {
    c()['ingresar']();
    backend.expectOne('/api/grupo/auth/login').flush({ desafio: 'd-2', mfaConfigurado: true });
    c()['codigo'] = '000000';
    c()['confirmar']();
    backend.expectOne('/api/grupo/auth/mfa').flush({ codigo: 'CREDENCIALES_INVALIDAS', mensaje: 'Código incorrecto' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(html().textContent).toContain('Código incorrecto');
    expect(TestBed.inject(GrupoSesionService).autenticado()).toBe(false);
  });

  it('el token del grupo solo viaja a /api/grupo y no se mezcla con el de la empresa', () => {
    c()['ingresar']();
    backend.expectOne('/api/grupo/auth/login').flush({ desafio: 'd', mfaConfigurado: true });
    c()['codigo'] = '123456';
    c()['confirmar']();
    backend.expectOne('/api/grupo/auth/mfa').flush(sesionGrupo);

    const http = TestBed.inject(HttpClient);
    http.get('/api/grupo/empresas').subscribe();
    expect(backend.expectOne('/api/grupo/empresas').request.headers.get('Authorization')).toBe('Bearer jwt-grupo');
    http.get('/api/ordenes').subscribe();
    expect(backend.expectOne('/api/ordenes').request.headers.has('Authorization')).toBe(false);
    expect(TestBed.inject(SesionService).tokenActual()).toBeNull();
  });
});
