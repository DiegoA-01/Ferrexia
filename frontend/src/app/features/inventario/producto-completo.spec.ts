import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { Inventario } from './inventario';

/** Spec 026: crear un producto completo sin salir del formulario. */
describe('Producto completo', () => {
  let backend: HttpTestingController;

  /** @returns la pantalla sin cargar la lista (solo se prueba el formulario) */
  const crear = () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: { tieneRol: () => true, tieneModulo: () => true, soloLectura: () => false,
          empresa: signal(null) } }],
    });
    backend = TestBed.inject(HttpTestingController);
    return TestBed.createComponent(Inventario).componentInstance as unknown as Record<string, any>;
  };

  /** Abre "Nuevo producto" y responde unidades, impuestos y proveedores. */
  const abrirNuevo = (c: Record<string, any>) => {
    c['abrir']('nuevo');
    backend.expectOne(r => r.url === '/api/unidades-medida').flush([{ id: 1, codigo: 'UND', nombre: 'Unidad' }]);
    backend.expectOne('/api/ventas/impuestos').flush([{ id: 5, codigo: 'IVA5', nombre: 'IVA 5 %', activo: true },
      { id: 6, codigo: 'EXC', nombre: 'Excluido', activo: false }]);
    backend.expectOne(r => r.url === '/api/proveedores').flush({ contenido: [{ id: 7, razonSocial: 'Electrosur' }],
      pagina: 0, tamano: 100, totalElementos: 1, totalPaginas: 1 });
  };

  afterEach(() => backend.verify());

  it('ofrece los impuestos activos y los proveedores, y envía impuesto, proveedor y fecha del costo', () => {
    const c = crear();
    abrirNuevo(c);
    expect(c['impuestos']().map((t: { id: number }) => t.id)).toEqual([5]);
    c['nuevo'].setValue({ codigo: 'BRK-30', nombre: 'Breaker 30 A', categoriaId: 3, unidadMedidaId: 1, stockMinimo: 2,
      stockMaximo: 10, costoReferencia: 25000, tarifaImpuestoId: 5, proveedorReferenciaId: 7, costoReferenciaFecha: '2026-11-20' });
    c['crearProducto']();
    const r = backend.expectOne(x => x.url === '/api/productos' && x.method === 'POST');
    expect(r.request.body.tarifaImpuestoId).toBe(5);
    expect(r.request.body.proveedorReferenciaId).toBe(7);
    expect(r.request.body.costoReferenciaFecha).toBe('2026-11-20');
    r.flush({ id: 20, codigo: 'BRK-30' });
    backend.match(() => true).forEach(x => x.flush([]));
  });

  it('crea una categoría al vuelo, la deja elegida y no pierde lo escrito', () => {
    const c = crear();
    abrirNuevo(c);
    c['nuevo'].patchValue({ codigo: 'BRK-30', nombre: 'Breaker 30 A' });
    c['abrirAlVuelo']('categoria');
    c['nombreNuevo'].set('  Breakers ');
    c['guardarAlVuelo']();
    const r = backend.expectOne(x => x.url === '/api/categorias' && x.method === 'POST');
    expect(r.request.body).toEqual({ nombre: 'Breakers' });
    r.flush({ id: 9, nombre: 'Breakers' });
    expect(c['nuevo'].controls.categoriaId.value).toBe(9);
    expect(c['nuevo'].controls.nombre.value).toBe('Breaker 30 A');
    expect(c['alVuelo']()).toBeNull();
  });

  it('crea un proveedor al vuelo solo con el nombre y lo deja elegido', () => {
    const c = crear();
    abrirNuevo(c);
    c['abrirAlVuelo']('proveedor');
    c['nombreNuevo'].set('Ferretería El Clavo');
    c['guardarAlVuelo']();
    const r = backend.expectOne(x => x.url === '/api/proveedores' && x.method === 'POST');
    expect(r.request.body).toEqual({ razonSocial: 'Ferretería El Clavo', nit: undefined });
    r.flush({ id: 12, razonSocial: 'Ferretería El Clavo' });
    expect(c['nuevo'].controls.proveedorReferenciaId.value).toBe(12);
    // queda en la lista junto al que ya había, por nombre
    expect(c['proveedores']().map((p: { razonSocial: string }) => p.razonSocial)).toEqual(['Electrosur', 'Ferretería El Clavo']);
  });

  it('sin nombre no crea nada', () => {
    const c = crear();
    c['abrirAlVuelo']('categoria');
    c['guardarAlVuelo']();
    backend.expectNone(x => x.method === 'POST');
  });

  it('D17: marca y filtra los productos sobre el máximo', () => {
    const c = crear();
    c['filas'].set([
      { productoId: 1, nombre: 'Cable', stock: 120, stockMinimo: 10, stockMaximo: 100, bajoMinimo: false },
      { productoId: 2, nombre: 'Breaker', stock: 50, stockMinimo: 10, stockMaximo: 100, bajoMinimo: false },
      { productoId: 3, nombre: 'Pintura', stock: 5, stockMinimo: 1, stockMaximo: null, bajoMinimo: false },
    ]);
    expect(c['totalSobreMaximo']()).toBe(1);
    expect(c['estado'](c['filas']()[0]).texto).toBe('Sobre el máximo');
    expect(c['estado'](c['filas']()[1]).texto).toBe('Normal');
    c['soloSobreMaximo'].set(true);
    expect(c['filasVisibles']().map((f: { productoId: number }) => f.productoId)).toEqual([1]);
  });
});
