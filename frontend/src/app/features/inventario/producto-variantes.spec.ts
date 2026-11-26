import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { Inventario } from './inventario';
import { ProductoVariantes, combinar, separarValores } from './producto-variantes';

/** Spec 028: un producto con atributos arma sus variantes, cada una con su código, precio y stock. */
describe('Variantes de producto', () => {
  let backend: HttpTestingController;

  const preparar = () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: { tieneRol: () => true, tieneModulo: () => true, soloLectura: () => false,
          empresa: signal(null) } }],
    });
    backend = TestBed.inject(HttpTestingController);
  };

  /**
   * @param productoId producto general que se edita, o null para uno nuevo
   * @returns la tabla de variantes con el código "TD"
   */
  const tabla = (productoId: number | null = null) => {
    preparar();
    const f: ComponentFixture<ProductoVariantes> = TestBed.createComponent(ProductoVariantes);
    f.componentRef.setInput('codigoBase', 'td');
    f.componentRef.setInput('productoId', productoId);
    f.detectChanges();
    return { f, c: f.componentInstance as unknown as Record<string, any> };
  };

  afterEach(() => backend.verify());

  it('separa valores sin repetir y combina en el orden de los atributos', () => {
    preparar();
    expect(separarValores(' 1", 2" ,, 1" ')).toEqual(['1"', '2"']);
    const c = combinar([{ nombre: 'Medida', valores: ['1', '2'] }, { nombre: 'Color', valores: ['Negro', 'Blanco'] }]);
    expect(c).toEqual([{ Medida: '1', Color: 'Negro' }, { Medida: '1', Color: 'Blanco' }, { Medida: '2', Color: 'Negro' },
      { Medida: '2', Color: 'Blanco' }]);
  });

  it('arma una fila por combinación con su código propuesto, "Llenar todos" copia y conserva lo escrito', () => {
    const { f, c } = tabla();
    c['cambiarAtributo'](0, 'nombre', 'Medida');
    c['cambiarAtributo'](0, 'valores', '1", 1 1/2"');
    c['agregarAtributo']();
    c['cambiarAtributo'](1, 'nombre', 'Color');
    c['cambiarAtributo'](1, 'valores', 'Negro, Blanco');
    f.detectChanges();
    const filas = c['filas']();
    expect(filas.length).toBe(4);
    expect(filas.map((x: { codigo: string }) => x.codigo)).toEqual(['TD-1-NEGRO', 'TD-1-BLANCO', 'TD-112-NEGRO', 'TD-112-BLANCO']);
    expect((f.nativeElement as HTMLElement).textContent).toContain('Combinaciones · 4');

    filas[0].codigoBarras = '770001';
    c['relleno'] = { costoReferencia: 300, precio: 450, stockMinimo: 10, stockMaximo: null };
    c['llenarTodos']();
    // otro valor no borra lo ya escrito en las demás filas
    c['cambiarAtributo'](1, 'valores', 'Negro, Blanco, Gris');
    const datos = c['datos']();
    expect(datos.atributos).toEqual([{ nombre: 'Medida', valores: ['1"', '1 1/2"'] }, { nombre: 'Color', valores: ['Negro', 'Blanco', 'Gris'] }]);
    expect(datos.variantes.length).toBe(6);
    expect(datos.variantes[0]).toEqual({ valores: { Medida: '1"', Color: 'Negro' }, codigo: 'TD-1-NEGRO', codigoBarras: '770001',
      costoReferencia: 300, precio: 450, stockMinimo: 10, stockMaximo: undefined });
    expect(datos.variantes[2].precio).toBeUndefined();
    expect(c['valido']()).toBe(true);
  });

  it('más de 100 combinaciones no se pueden crear', () => {
    const { f, c } = tabla();
    const once = Array.from({ length: 11 }, (_, i) => i + 1).join(',');
    c['cambiarAtributo'](0, 'nombre', 'A');
    c['cambiarAtributo'](0, 'valores', once);
    c['agregarAtributo']();
    c['cambiarAtributo'](1, 'nombre', 'B');
    c['cambiarAtributo'](1, 'valores', once);
    f.detectChanges();
    expect((f.nativeElement as HTMLElement).textContent).toContain('Salen 121 combinaciones');
    expect(c['valido']()).toBe(false);
  });

  it('al editar un producto general muestra las que existen, pide datos solo de las nuevas y desactiva las que salen', () => {
    const { f, c } = tabla(50);
    backend.expectOne('/api/productos/50/variantes').flush({
      agrupador: { id: 50, codigo: 'TD', esAgrupador: true },
      atributos: [{ nombre: 'Medida', valores: ['1"', '2"'] }],
      variantes: [
        { id: 51, codigo: 'TD-1', nombre: 'Tornillo · 1"', activo: true, stockTotal: 8, combinacion: { Medida: '1"' } },
        { id: 52, codigo: 'TD-2', nombre: 'Tornillo · 2"', activo: true, stockTotal: 0, combinacion: { Medida: '2"' } },
      ],
    });
    f.detectChanges();
    c['cambiarAtributo'](0, 'valores', '1", 3"');
    f.detectChanges();
    const texto = (f.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Ya existe · stock 8');
    expect(texto).toContain('Se desactiva');
    expect(c['nuevas']().length).toBe(1);

    vi.spyOn(window, 'confirm').mockReturnValue(true);
    c['guardar']();
    const r = backend.expectOne(x => x.url === '/api/productos/50/variantes' && x.method === 'PUT');
    expect(r.request.body.atributos).toEqual([{ nombre: 'Medida', valores: ['1"', '3"'] }]);
    expect(r.request.body.variantes.map((v: { codigo: string }) => v.codigo)).toEqual(['TD-3']);
    r.flush({ agrupador: { id: 50 }, atributos: [{ nombre: 'Medida', valores: ['1"', '3"'] }], variantes: [] });
  });

  it('en inventario el producto general despliega sus variantes y el alta con variantes va al backend', () => {
    preparar();
    const f = TestBed.createComponent(Inventario);
    const c = f.componentInstance as unknown as Record<string, any>;
    f.detectChanges();
    backend.expectOne(r => r.url === '/api/categorias').flush([]);
    backend.expectOne(r => r.url.includes('sugerencias') || r.url.includes('reorden')).flush([]);
    backend.expectOne(r => r.url === '/api/reportes/inventario').flush({ productos: 2, bajoMinimo: 0, valorTotal: 0, items: [
      { productoId: 50, codigo: 'TD', nombre: 'Tornillo drywall', stock: 13, esAgrupador: true },
      { productoId: 51, codigo: 'TD-1', nombre: 'Tornillo drywall · 1"', stock: 8, stockMinimo: 1, agrupadorId: 50 },
      { productoId: 52, codigo: 'TD-2', nombre: 'Tornillo drywall · 2"', stock: 5, stockMinimo: 1, agrupadorId: 50 },
      { productoId: 60, codigo: 'P-1', nombre: 'Cable', stock: 3, stockMinimo: 1 },
    ] });
    f.detectChanges();
    expect(c['filasVisibles']().map((x: { codigo: string }) => x.codigo)).toEqual(['TD', 'P-1']);
    expect((f.nativeElement as HTMLElement).textContent).toContain('2 variantes');
    c['alternar']({ productoId: 50 });
    expect(c['filasVisibles']().map((x: { codigo: string }) => x.codigo)).toEqual(['TD', 'TD-1', 'TD-2', 'P-1']);

    c['abrir']('nuevo');
    backend.match(() => true).forEach(x => x.flush([]));
    c['conVariantes'].set(true);
    f.detectChanges();
    c['nuevo'].patchValue({ codigo: 'TD', nombre: 'Tornillo drywall', categoriaId: 3, unidadMedidaId: 1 });
    const t = c['variantes']() as unknown as Record<string, any>;
    t['cambiarAtributo'](0, 'nombre', 'Medida');
    t['cambiarAtributo'](0, 'valores', '1"');
    f.detectChanges();
    c['crearConVariantes']();
    const r = backend.expectOne(x => x.url === '/api/productos/con-variantes' && x.method === 'POST');
    expect(r.request.body).toEqual(expect.objectContaining({ codigo: 'TD', nombre: 'Tornillo drywall', categoriaId: 3, unidadMedidaId: 1,
      atributos: [{ nombre: 'Medida', valores: ['1"'] }] }));
    expect(r.request.body.variantes[0].codigo).toBe('TD-1');
    r.flush({ agrupador: { id: 50, codigo: 'TD' }, atributos: [], variantes: [{ id: 51 }] });
    backend.match(() => true).forEach(x => x.flush({ items: [] }));
  });
});
