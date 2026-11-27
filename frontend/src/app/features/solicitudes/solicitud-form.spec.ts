import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { SolicitudForm } from './solicitud-form';

const producto = (id: number, nombre: string) => ({ id, codigo: 'P' + id, nombre, costoReferencia: 4500, stockTotal: 10, unidadMedida: { id: 1, codigo: 'UND' } });
const pagina = (contenido: unknown[]) => ({ contenido, pagina: 0, tamano: 10, totalElementos: contenido.length, totalPaginas: 1 });

describe('Nueva solicitud: elegir productos', () => {
  let fixture: ComponentFixture<SolicitudForm>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;

  const crear = (roles: string[], permisos: string[] = []) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: {
          tieneRol: (r?: string[]) => !r?.length || r.some(x => roles.includes(x)),
          tienePermiso: (p: string) => permisos.includes(p),
        } }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SolicitudForm);
    fixture.detectChanges();
    backend.expectOne('/api/solicitudes/proveedores').flush([{ id: 7, razonSocial: 'Ferro Andes' }, { id: 8, razonSocial: 'Eléctricos Sur' }]);
  };
  const productos = () => backend.expectOne(r => r.url === '/api/productos');

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  it('al poner el cursor en el campo ya trae el catálogo, sin escribir nada', () => {
    crear(['SOLICITANTE']);
    c()['abrirLista']();
    vi.advanceTimersByTime(250);
    const p = productos();
    expect(p.request.params.has('texto')).toBe(false);
    p.flush(pagina([producto(1, 'Cemento gris 50 kg'), producto(2, 'Tornillo 1 pulgada')]));

    expect(c()['resultados']().length).toBe(2);
    expect(c()['listaAbierta']()).toBe(true);
  });

  it('si varias palabras no coinciden prueba con la primera y avisa que son parecidos', () => {
    crear(['SOLICITANTE']);
    c()['buscar']('tornillo 3 octavos');
    vi.advanceTimersByTime(250);
    productos().flush(pagina([]));
    const segunda = productos();
    expect(segunda.request.params.get('texto')).toBe('tornillo');
    segunda.flush(pagina([producto(2, 'Tornillo 1 pulgada')]));

    expect(c()['parecidos']()).toBe(true);
    expect(c()['resultados']().length).toBe(1);
  });

  it('sin ninguna coincidencia queda el aviso, no un vacío mudo', () => {
    crear(['SOLICITANTE']);
    c()['buscar']('xyz');
    vi.advanceTimersByTime(250);
    productos().flush(pagina([]));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No hay productos que coincidan');
    expect(fixture.nativeElement.textContent).toContain('Pide a Compras que lo cree');
  });

  it('agregar un producto lo pone en la solicitud con cantidad 1 y cierra la lista', () => {
    crear(['SOLICITANTE']);
    c()['agregar'](producto(1, 'Cemento gris 50 kg'));

    expect(c()['lineas']().length).toBe(1);
    expect(c()['lineas']()[0].cantidad).toBe(1);
    expect(c()['listaAbierta']()).toBe(false);
  });

  it('quien administra compras crea el producto desde la misma pantalla y queda agregado', () => {
    crear(['ADMIN_COMPRAS']);
    c()['abrirCrear']();
    backend.expectOne(r => r.url === '/api/categorias').flush([{ id: 5, nombre: 'Eléctrico' }]);
    backend.expectOne(r => r.url === '/api/unidades-medida').flush([{ id: 1, codigo: 'UND', nombre: 'Unidad' }]);
    c()['nuevoCodigo'].set('CAB-14');
    c()['nuevoNombre'].set('Cable 14 AWG');
    c()['nuevaCategoria'].set(5);
    c()['nuevaUnidad'].set(1);
    c()['nuevoCosto'].set(2500);
    c()['crearProducto']();

    const p = backend.expectOne('/api/productos');
    expect(p.request.method).toBe('POST');
    expect(p.request.body).toMatchObject({ codigo: 'CAB-14', nombre: 'Cable 14 AWG', categoriaId: 5, unidadMedidaId: 1, costoReferencia: 2500 });
    p.flush(producto(9, 'Cable 14 AWG'));

    expect(c()['lineas']().map((l: any) => l.producto.id)).toEqual([9]);
    expect(c()['creando']()).toBe(false);
  });

  it('quien no administra compras no ve el botón de crear', () => {
    crear(['SOLICITANTE']);
    expect(c()['puedeCrear']()).toBe(false);
  });

  it('spec 033: a proveedores específicos exige elegir uno y envía los elegidos', () => {
    crear(['SOLICITANTE']);
    c()['agregar'](producto(1, 'Cemento gris 50 kg'));
    c()['motivo'].set('Obra del cliente');
    c()['cambiarModo']('ESPECIFICA');
    c()['guardar']();
    backend.expectNone('/api/solicitudes');

    c()['alternarProveedor'](8);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Elegidos: Eléctricos Sur');
    c()['guardar']();
    const r = backend.expectOne('/api/solicitudes');
    expect(r.request.body).toMatchObject({ modoProveedor: 'ESPECIFICA', proveedorIds: [8] });
    r.flush({ id: 3, consecutivo: 'SOL-000003', estado: 'BORRADOR', modoProveedor: 'ESPECIFICA', proveedores: [{ id: 8, razonSocial: 'Eléctricos Sur' }], lineas: [] });
  });

  const conAlerta = () => {
    c()['guardada'].set({ id: 3, consecutivo: 'SOL-000003', estado: 'BORRADOR', lineas: [] });
    c()['enviar']();
    backend.expectOne('/api/solicitudes/3/enviar').flush(
      { codigo: 'PRESUPUESTO_EXCEDIDO', mensaje: 'Excede el presupuesto. Ajuste las cantidades' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
  };

  it('spec 033: sin el permiso, la alerta bloquea y no deja enviar con justificación', () => {
    crear(['SOLICITANTE']);
    conAlerta();
    const html = fixture.nativeElement as HTMLElement;
    expect(html.querySelector('[role=alert]')?.textContent).toContain('No se puede enviar así');
    expect(html.querySelector('#just')).toBeNull();
    expect(html.textContent).not.toContain('Enviar con justificación');
    c()['enviar']();
    backend.expectNone('/api/solicitudes/3/enviar');
  });

  it('spec 033: con el permiso SOLICITUD_EXCEPCION se puede enviar con justificación', () => {
    crear(['ADMIN_SISTEMA'], ['SOLICITUD_EXCEPCION']);
    conAlerta();
    expect((fixture.nativeElement as HTMLElement).querySelector('#just')).not.toBeNull();
    c()['justificacion'].set('Obra urgente');
    c()['enviar']();
    const r = backend.expectOne('/api/solicitudes/3/enviar');
    expect(r.request.body).toEqual({ justificacionExcepcion: 'Obra urgente' });
    r.flush({ id: 3, consecutivo: 'SOL-000003', estado: 'PENDIENTE_APROBACION' });
  });
});
