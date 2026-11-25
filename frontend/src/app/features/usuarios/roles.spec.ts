import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { ROLES_POS, INTERNOS } from '../../core/navegacion/menu';
import { SesionService } from '../../core/sesion/sesion.service';
import { Roles } from './roles';
import { Usuarios } from './usuarios';

const catalogo = [
  { codigo: 'TURNO_OPERAR', nombre: 'Abrir, mover efectivo y cerrar su turno', modulo: 'CAJA' },
  { codigo: 'CAJA_VER', nombre: 'Ver las cajas', modulo: 'CAJA' },
  { codigo: 'PORTAL_PROVEEDOR', nombre: 'Usar el portal del proveedor', modulo: 'PORTAL' },
];
const roles = [
  { id: 1, codigo: 'CAJERO', nombre: 'Cajero', plantilla: true, permisos: ['TURNO_OPERAR', 'CAJA_VER'] },
  { id: 9, codigo: 'E1_CAJERO_NOCHE', nombre: 'Cajero de noche', plantilla: false, permisos: ['TURNO_OPERAR'] },
];

describe('Permisos en la pantalla (spec 022)', () => {
  let backend: HttpTestingController;

  const preparar = () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    return TestBed.inject(SesionService);
  };

  afterEach(() => backend.verify());

  it('con los permisos cargados decide el permiso: un rol propio abre el punto de venta', () => {
    const sesion = preparar();
    sesion.usuario.set({ id: 3, roles: ['E1_CAJERO_NOCHE'] });
    // sin permisos cargados, decide el rol (como antes)
    expect(sesion.tieneRol(ROLES_POS)).toBe(false);
    sesion.permisos.set(new Set(['VENTA_COTIZAR']));
    expect(sesion.tieneRol(ROLES_POS)).toBe(true);
    expect(sesion.tienePermiso('VENTA_COTIZAR')).toBe(true);
    // una lista sin permiso equivalente sigue mirando el rol
    expect(sesion.tieneRol(['ADMIN_SISTEMA'])).toBe(false);
    // "cualquier interno" vale para un rol propio y no para el proveedor
    expect(sesion.tieneRol(INTERNOS)).toBe(true);
    sesion.usuario.set({ id: 4, roles: ['PROVEEDOR'] });
    expect(sesion.tieneRol(INTERNOS)).toBe(false);
  });

  it('con un rol de siempre el resultado es el mismo que antes', () => {
    const sesion = preparar();
    sesion.usuario.set({ id: 3, roles: ['CAJERO'] });
    expect(sesion.tieneRol(ROLES_POS)).toBe(true);
    sesion.permisos.set(new Set(['VENTA_COTIZAR', 'TURNO_OPERAR']));
    expect(sesion.tieneRol(ROLES_POS)).toBe(true);
  });

  it('las plantillas se ven pero no se cambian; copiar arma un rol propio con sus permisos', () => {
    const sesion = preparar();
    sesion.usuario.set({ id: 1, roles: ['ADMIN_SISTEMA'] });
    sesion.permisos.set(new Set(['ROL_GESTIONAR', 'USUARIO_VER']));
    const f = TestBed.createComponent(Roles);
    f.detectChanges();
    backend.expectOne('/api/roles').flush(roles);
    backend.expectOne('/api/roles/permisos').flush(catalogo);
    f.detectChanges();
    const c = f.componentInstance as unknown as Record<string, any>;
    const raiz = f.nativeElement as HTMLElement;
    expect(raiz.textContent).toContain('Plantilla');
    expect(raiz.textContent).toContain('Propio');

    c['ver'](roles[0]);
    f.detectChanges();
    expect(c['soloLectura']()).toBe(true);
    expect([...raiz.querySelectorAll<HTMLInputElement>('.permiso input')].every(i => i.disabled)).toBe(true);
    // el portal del proveedor no se ofrece en roles propios
    expect(raiz.textContent).not.toContain('Usar el portal del proveedor');

    c['nuevo'](roles[0]);
    c['cambiar']('nombre', 'Cajero de día');
    c['alternar']('CAJA_VER');
    c['guardar']();
    const r = backend.expectOne(x => x.url === '/api/roles' && x.method === 'POST');
    expect(r.request.body).toEqual({ nombre: 'Cajero de día', descripcion: undefined, permisos: ['TURNO_OPERAR'] });
    r.flush({ id: 10, nombre: 'Cajero de día' });
    backend.expectOne('/api/roles').flush(roles);
  });

  it('la ficha del trabajador suma y quita permisos sueltos', () => {
    const sesion = preparar();
    sesion.usuario.set({ id: 1, roles: ['ADMIN_SISTEMA'] });
    sesion.permisos.set(new Set(['USUARIO_GESTIONAR', 'USUARIO_VER']));
    const f = TestBed.createComponent(Usuarios);
    f.detectChanges();
    backend.expectOne('/api/roles').flush(roles);
    backend.match(r => r.url.includes('centros-costo')).forEach(r => r.flush([]));
    backend.expectOne(r => r.url === '/api/usuarios').flush({ contenido: [{ id: 5, nombreCompleto: 'Cajera', roles: ['CAJERO'], activo: true, tipoCredencial: 'PIN' }] });
    f.detectChanges();
    const raiz = f.nativeElement as HTMLElement;
    expect(raiz.textContent).toContain('PIN');
    const c = f.componentInstance as unknown as Record<string, any>;
    c['abrirPermisos']({ id: 5, nombreCompleto: 'Cajera' });
    backend.expectOne('/api/usuarios/5/permisos').flush([
      { codigo: 'CAJA_VER', nombre: 'Ver las cajas', modulo: 'CAJA', delRol: true, ajuste: null, efectivo: true },
      { codigo: 'AUDITORIA_VER', nombre: 'Consultar la auditoría', modulo: 'AUDITORIA', delRol: false, ajuste: null, efectivo: false },
    ]);
    f.detectChanges();
    expect(raiz.textContent).toContain('Lo da su rol');
    c['ajustar']('CAJA_VER', 'QUITADO');
    c['ajustar']('AUDITORIA_VER', 'SUMADO');
    c['guardarPermisos']();
    const r = backend.expectOne(x => x.url === '/api/usuarios/5/permisos' && x.method === 'PUT');
    expect(r.request.body).toEqual({ ajustes: [{ permiso: 'CAJA_VER', concedido: false }, { permiso: 'AUDITORIA_VER', concedido: true }] });
    r.flush([]);
  });
});
