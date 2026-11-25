import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { routes } from '../../../app.routes';
import { SesionService } from '../../../core/sesion/sesion.service';
import { CargaPreciosExcel } from './carga-precios';
import { CargaProductos } from './carga-productos';
import { Importacion } from './importacion';

/** Sesión de prueba: roles, módulos y si la empresa está en solo lectura. */
const sesion = (roles: string[], opciones: { modulos?: string[]; soloLectura?: boolean } = {}) => ({
  tieneRol: (r: readonly string[]) => r.some(x => roles.includes(x)),
  tieneModulo: (m?: string) => !m || (opciones.modulos ?? ['INVENTARIO', 'VENTAS']).includes(m),
  soloLectura: () => !!opciones.soloLectura,
});

describe('Importación de datos (spec 019)', () => {
  let fixture: ComponentFixture<Importacion>;
  let backend: HttpTestingController;
  const raiz = () => fixture.nativeElement as HTMLElement;

  /** @param s sesión simulada */
  const crear = (s: ReturnType<typeof sesion>) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: SesionService, useValue: s }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Importacion);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('el dueño ve la carga de precios pero no la de productos', () => {
    crear(sesion(['ADMIN_SISTEMA']));
    expect(raiz().querySelector('sc-carga-precios')).not.toBeNull();
    expect(raiz().querySelector('sc-carga-productos')).toBeNull();
  });

  it('el jefe de compras ve productos y, si el dueño le delegó los precios, también precios', () => {
    crear(sesion(['ADMIN_COMPRAS']));
    backend.expectOne(r => r.url === '/api/ventas/precios').flush({ puedeEditar: true, filas: [] });
    fixture.detectChanges();
    expect(raiz().querySelector('sc-carga-productos')).not.toBeNull();
    expect(raiz().querySelector('sc-carga-precios')).not.toBeNull();
  });

  it('sin delegación, el jefe de compras no ve la carga de precios', () => {
    crear(sesion(['ADMIN_COMPRAS']));
    backend.expectOne(r => r.url === '/api/ventas/precios').flush({ puedeEditar: false, filas: [] });
    fixture.detectChanges();
    expect(raiz().querySelector('sc-carga-productos')).not.toBeNull();
    expect(raiz().querySelector('sc-carga-precios')).toBeNull();
  });

  it('sin el módulo de inventario no ofrece la carga de productos', () => {
    crear(sesion(['ADMIN_COMPRAS'], { modulos: ['VENTAS'] }));
    backend.expectOne(r => r.url === '/api/ventas/precios').flush({ puedeEditar: false, filas: [] });
    fixture.detectChanges();
    expect(raiz().querySelector('sc-carga-productos')).toBeNull();
    expect(raiz().textContent).toContain('No tienes cargas por Excel disponibles');
  });

  it('en solo lectura (empresa morosa) no ofrece ninguna carga', () => {
    crear(sesion(['ADMIN_SISTEMA', 'ADMIN_COMPRAS'], { soloLectura: true }));
    expect(raiz().querySelector('sc-carga-productos')).toBeNull();
    expect(raiz().querySelector('sc-carga-precios')).toBeNull();
  });
});

describe('Cargas por Excel movidas a Importación (spec 019)', () => {
  let backend: HttpTestingController;

  const preparar = () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
  };

  afterEach(() => backend.verify());

  it('productos: se revisa primero y luego se aplica en el mismo endpoint de antes', () => {
    preparar();
    const f = TestBed.createComponent(CargaProductos);
    f.detectChanges();
    const c = f.componentInstance as unknown as Record<string, any>;
    let aplicada = false;
    f.componentInstance.aplicada.subscribe(() => (aplicada = true));
    c['elegirArchivo']({ target: { files: [new File(['x'], 'productos.xlsx')] } });
    c['cargarExcel'](true);
    const revision = backend.expectOne(r => r.url === '/api/productos/carga-masiva');
    expect(revision.request.params.get('soloValidar')).toBe('true');
    revision.flush({ filas: 3, creados: 2, actualizados: 1, categoriasCreadas: 0, errores: [], aplicado: false });
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).textContent).toContain('3 filas · 2 por crear · 1 por actualizar');
    c['cargarExcel'](false);
    const aplicar = backend.expectOne(r => r.url === '/api/productos/carga-masiva');
    expect(aplicar.request.params.get('soloValidar')).toBe('false');
    aplicar.flush({ filas: 3, creados: 2, actualizados: 1, categoriasCreadas: 0, errores: [], aplicado: true });
    expect(aplicada).toBe(true);
  });

  it('precios: el Excel se revisa primero y solo se aplica si no tiene errores', () => {
    preparar();
    const f = TestBed.createComponent(CargaPreciosExcel);
    f.detectChanges();
    const c = f.componentInstance as unknown as Record<string, any>;
    const texto = () => (f.nativeElement as HTMLElement).textContent ?? '';
    c['elegirArchivo']({ target: { files: [new File(['x'], 'precios.xlsx')], value: 'precios.xlsx' } });
    const revision = backend.expectOne(r => r.url === '/api/ventas/precios/carga');
    expect(revision.request.params.get('soloValidar')).toBe('true');
    revision.flush({ soloValidar: true, aplicado: false, filas: 2, precios: 2, errores: [] });
    f.detectChanges();
    expect(texto()).toContain('se fijarán 2 precios');
    c['subir'](false);
    const aplicar = backend.expectOne(r => r.url === '/api/ventas/precios/carga');
    expect(aplicar.request.params.get('soloValidar')).toBe('false');
    aplicar.flush({ soloValidar: false, aplicado: true, filas: 2, precios: 2, errores: [] });
    f.detectChanges();
    expect(texto()).toContain('2 precios fijados');
  });

  it('precios: con errores muestra las filas y no ofrece aplicar', () => {
    preparar();
    const f = TestBed.createComponent(CargaPreciosExcel);
    f.detectChanges();
    const c = f.componentInstance as unknown as Record<string, any>;
    c['elegirArchivo']({ target: { files: [new File(['x'], 'precios.xlsx')], value: '' } });
    backend.expectOne(r => r.url === '/api/ventas/precios/carga')
      .flush({ soloValidar: true, aplicado: false, filas: 2, precios: 1, errores: [{ fila: 3, mensaje: 'el producto NO-EXISTE no existe' }] });
    f.detectChanges();
    const texto = (f.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('No se cambió ningún precio');
    expect(texto).toContain('Fila 3: el producto NO-EXISTE no existe');
    expect(texto).not.toContain('Fijar los precios');
  });
});

describe('Rutas de la spec 019', () => {
  const hijas = () => routes.flatMap(r => r.children ?? []);

  it('la ruta vieja de reportes de ventas redirige a la nueva', () => {
    const vieja = hijas().find(r => r.path === 'ventas/reportes');
    expect(vieja?.redirectTo).toBe('reportes/ventas');
    expect(hijas().some(r => r.path === 'reportes/ventas' && r.loadComponent)).toBe(true);
  });

  it('Importación de datos queda antes de la ruta genérica de catálogos', () => {
    const rutas = hijas().map(r => r.path);
    expect(rutas.indexOf('configuracion/importacion')).toBeGreaterThan(-1);
    expect(rutas.indexOf('configuracion/importacion')).toBeLessThan(rutas.indexOf('configuracion/:catalogo'));
  });
});
