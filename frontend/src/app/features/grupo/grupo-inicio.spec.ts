import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { authInterceptor } from '../../core/http/auth.interceptor';
import { GrupoSesionService } from '../../core/sesion/grupo-sesion.service';
import { grupoGuard } from '../../core/sesion/guards';
import { SesionService } from '../../core/sesion/sesion.service';
import { GrupoInicio } from './grupo-inicio';

const empresas = [
  { id: 5, razonSocial: 'Ferretería Norte', codigoPublico: 'norte', estado: 'ACTIVA' },
  { id: 6, razonSocial: 'Eléctricos Sur', codigoPublico: 'sur', estado: 'SUSPENDIDA' },
];
const tablero = {
  desde: '2026-11-01', hasta: '2026-11-27',
  empresas: [
    { empresaId: 5, razonSocial: 'Ferretería Norte', ventasNetas: 1000000, compras: 400000, inventarioValorizado: 2500000 },
    { empresaId: 6, razonSocial: 'Eléctricos Sur', ventasNetas: 500000, compras: 100000, inventarioValorizado: 800000 },
  ],
  total: { ventasNetas: 1500000, compras: 500000, inventarioValorizado: 3300000 },
};
const loginEmpresa = {
  token: 'jwt-empresa', tipo: 'Bearer', expiraEnSegundos: 900, refreshToken: 'r-emp',
  usuario: { id: 70, nombre: 'Dueña', correo: 'dueno@x.co', roles: ['ADMIN_SISTEMA'] },
};

describe('Zona del grupo: empresas, entrar y tablero (spec 035)', () => {
  let fixture: ComponentFixture<GrupoInicio>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const html = () => fixture.nativeElement as HTMLElement;

  /** Abre la sesión del grupo con un token de prueba. */
  const abrirSesionGrupo = () => {
    TestBed.inject(GrupoSesionService).confirmar('d', '123456').subscribe();
    backend.expectOne('/api/grupo/auth/mfa').flush({ token: 'jwt-grupo', nombre: 'Dueña', grupoNombre: 'Grupo Norte' });
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GrupoInicio);
    fixture.detectChanges();
    backend.expectOne('/api/grupo/empresas').flush(empresas);
    backend.expectOne(r => r.url === '/api/grupo/tablero').flush(tablero);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('lista las empresas con su botón Entrar (deshabilitado si está suspendida)', () => {
    const botones = [...html().querySelectorAll<HTMLButtonElement>('.empresa button')];
    expect(botones.map(b => b.textContent?.trim())).toEqual(['Entrar', 'Entrar']);
    expect(botones[0].disabled).toBe(false);
    expect(botones[1].disabled).toBe(true);
  });

  it('el tablero muestra lo que calculó el backend, por empresa y el total, sin sumar en pantalla', () => {
    expect(html().textContent).toContain('Ferretería Norte');
    expect(html().textContent).toContain('Total del grupo');
    expect(html().querySelectorAll('tfoot td').length).toBe(4);
    // el total es el que mandó el backend (el 1.500.000 no se calcula aquí)
    expect(html().querySelector('tfoot')?.textContent).toMatch(/1\.500\.000/);
  });

  it('el filtro de fechas vuelve a pedir el tablero con desde y hasta', () => {
    c()['desde'] = '2026-10-01';
    c()['hasta'] = '2026-10-31';
    c()['cargarTablero']();
    const r = backend.expectOne(q => q.url === '/api/grupo/tablero');
    expect(r.request.params.get('desde')).toBe('2026-10-01');
    expect(r.request.params.get('hasta')).toBe('2026-10-31');
    r.flush(tablero);
  });

  it('Entrar usa el token del grupo, arranca la sesión de empresa por el camino normal y abre /app/inicio', async () => {
    abrirSesionGrupo();
    html().querySelector<HTMLButtonElement>('.empresa button')!.click();
    const entrar = backend.expectOne('/api/grupo/entrar/5');
    expect(entrar.request.method).toBe('POST');
    expect(entrar.request.headers.get('Authorization')).toBe('Bearer jwt-grupo');
    entrar.flush(loginEmpresa);

    // el mismo contexto que carga el login normal de la empresa
    backend.expectOne('/api/empresa').flush({ razonSocial: 'Ferretería Norte' });
    backend.expectOne('/api/empresa/modulos').flush({ estado: 'ACTIVA', modulos: ['INVENTARIO'] });
    backend.expectOne('/api/perfil/permisos').flush(['TRASLADO_GRUPO_ENVIAR']);
    await fixture.whenStable();

    const sesion = TestBed.inject(SesionService);
    expect(sesion.autenticado()).toBe(true);
    expect(sesion.tokenActual()).toBe('jwt-empresa');
    expect(sesion.desdeGrupo()).toBe(true);
    expect(sessionStorage.getItem('sc.refresh')).toBe('r-emp');
    expect(TestBed.inject(Router).url).toBe('/app/inicio');
    expect(TestBed.inject(GrupoSesionService).autenticado()).toBe(true);
  });

  it('si entrar falla (404) no abre sesión y se puede volver a intentar', () => {
    abrirSesionGrupo();
    html().querySelector<HTMLButtonElement>('.empresa button')!.click();
    backend.expectOne('/api/grupo/entrar/5').flush({ codigo: 'NO_ENCONTRADO', mensaje: 'Empresa no encontrada' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(TestBed.inject(SesionService).autenticado()).toBe(false);
    expect(c()['entrando']()).toBeNull();
  });

  it('"Cambiar de empresa" cierra la sesión de empresa, conserva la del grupo y vuelve a /grupo', async () => {
    abrirSesionGrupo();
    const sesion = TestBed.inject(SesionService);
    sesion.iniciarDesdeGrupo(loginEmpresa).subscribe();
    backend.expectOne('/api/empresa').flush({});
    backend.expectOne('/api/empresa/modulos').flush({});
    backend.expectOne('/api/perfil/permisos').flush([]);
    expect(sesion.desdeGrupo()).toBe(true);

    sesion.volverAlGrupo();
    expect(backend.expectOne('/api/auth/logout').request.body).toEqual({ refreshToken: 'r-emp' });
    await fixture.whenStable();

    expect(sesion.autenticado()).toBe(false);
    expect(sesion.desdeGrupo()).toBe(false);
    expect(sessionStorage.getItem('sc.refresh')).toBeNull();
    const grupo = TestBed.inject(GrupoSesionService);
    expect(grupo.autenticado()).toBe(true);
    expect(grupo.tokenActual()).toBe('jwt-grupo');
    expect(TestBed.inject(Router).url).toBe('/grupo');
  });

  it('grupoGuard deja pasar con sesión de grupo y si no manda al ingreso del grupo', () => {
    const router = TestBed.inject(Router);
    const sin = TestBed.runInInjectionContext(() => grupoGuard({} as never, {} as never));
    expect(router.serializeUrl(sin as never)).toBe('/grupo/ingreso');
    abrirSesionGrupo();
    expect(TestBed.runInInjectionContext(() => grupoGuard({} as never, {} as never))).toBe(true);
  });
});
