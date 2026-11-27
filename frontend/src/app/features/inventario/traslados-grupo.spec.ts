import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Pagina, TrasladoResumen } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { TrasladosGrupo } from './traslados-grupo';

const bodegas = [{ id: 1, nombre: 'Central', activo: true }, { id: 2, nombre: 'Sede Norte', activo: true }];
const resumen = (id: number, sentido: string, estado: string): TrasladoResumen => ({
  id, consecutivo: `TG-000${id}`, estado: estado as never, sentido, empresaOrigenId: 1, empresaOrigenNombre: 'Ferretería Norte',
  empresaDestinoId: 2, empresaDestinoNombre: 'Eléctricos Sur', lineas: 1, enviadoEn: '2026-11-27T10:00:00',
});
const lista = (...t: TrasladoResumen[]): Pagina<TrasladoResumen> => ({ contenido: t, pagina: 0, tamano: 20, totalElementos: t.length, totalPaginas: 1 });
const detalle = (r: TrasladoResumen) => ({
  resumen: r, bodegaPropiaId: 1,
  lineas: [{ id: 91, productoId: 7, codigo: 'CAB-12', nombre: 'Cable 12 AWG', cantidad: 10, costoUnitario: 2500 }],
});

describe('Traslados entre empresas (spec 035)', () => {
  let fixture: ComponentFixture<TrasladosGrupo>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const html = () => fixture.nativeElement as HTMLElement;

  /** Crea la pantalla con los permisos dados y responde su carga inicial. */
  const crear = (permisos: string[], traslados = lista(resumen(1, 'ENVIADO', 'EN_TRANSITO'), resumen(2, 'RECIBIDO', 'EN_TRANSITO'))) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).permisos.set(new Set(permisos));
    fixture = TestBed.createComponent(TrasladosGrupo);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/traslados-grupo').flush(traslados);
    backend.expectOne(r => r.url === '/api/bodegas').flush(bodegas);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  /** Abre el detalle del traslado de la lista. */
  const abrir = (id: number, r: TrasladoResumen) => {
    c()['abrir'](r);
    backend.expectOne(`/api/traslados-grupo/${id}`).flush(detalle(r));
    fixture.detectChanges();
  };

  const textos = () => [...html().querySelectorAll('button')].map(b => b.textContent?.trim());

  it('lista lo enviado y lo recibido con su estado y la otra empresa', () => {
    crear(['TRASLADO_GRUPO_ENVIAR', 'TRASLADO_GRUPO_RECIBIR']);
    const filas = [...html().querySelectorAll('tbody tr')].map(f => f.textContent ?? '');
    expect(filas.length).toBe(2);
    expect(filas[0]).toContain('Enviado');
    expect(filas[0]).toContain('Eléctricos Sur');
    expect(filas[1]).toContain('Recibido');
    expect(filas[1]).toContain('Ferretería Norte');
    expect(filas[0]).toContain('En tránsito');
  });

  it('el botón "Nuevo traslado" solo aparece con el permiso de enviar', () => {
    crear(['TRASLADO_GRUPO_RECIBIR']);
    expect(textos().some(t => t?.includes('Nuevo traslado'))).toBe(false);
  });

  it('enviar: arma la lista, manda el traslado y muestra el 409 PRODUCTOS_SIN_PAREJA con los códigos', () => {
    crear(['TRASLADO_GRUPO_ENVIAR']);
    expect(textos().some(t => t?.includes('Nuevo traslado'))).toBe(true);
    c()['abrirNuevo']();
    backend.expectOne('/api/traslados-grupo/destinos').flush([{ id: 2, nombre: 'Eléctricos Sur' }]);
    fixture.detectChanges();

    c()['destinoId'].set(2);
    c()['bodegaOrigen'].set(1);
    c()['nota'].set('Reposición');
    c()['buscador'].opciones.set([{ id: 7, codigo: 'CAB-12', nombre: 'Cable 12 AWG' }]);
    c()['buscador'].texto.set('CAB-12 · Cable 12 AWG');
    c()['cantidad'].set(10);
    c()['agregarLinea']();
    expect(c()['lineas']()).toEqual([{ productoId: 7, codigo: 'CAB-12', nombre: 'Cable 12 AWG', cantidad: 10 }]);

    c()['enviar']();
    const r = backend.expectOne(q => q.url === '/api/traslados-grupo' && q.method === 'POST');
    expect(r.request.body).toEqual({ empresaDestinoId: 2, bodegaOrigenId: 1, lineas: [{ productoId: 7, cantidad: 10 }], nota: 'Reposición' });
    r.flush({ codigo: 'PRODUCTOS_SIN_PAREJA', mensaje: 'La empresa destino no tiene estos productos (se buscan por código o código de barras): CAB-12' },
      { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(html().querySelector('.aviso.ambar')?.textContent).toContain('no tiene estos productos');
    expect(html().querySelector('.aviso.ambar')?.textContent).toContain('CAB-12');
    expect(c()['nuevo']()).toBe(true);
    expect(c()['lineas']().length).toBe(1);
  });

  it('enviar bien: cierra el formulario, avisa y muestra el traslado creado', () => {
    crear(['TRASLADO_GRUPO_ENVIAR']);
    c()['abrirNuevo']();
    backend.expectOne('/api/traslados-grupo/destinos').flush([{ id: 2, nombre: 'Eléctricos Sur' }]);
    c()['destinoId'].set(2);
    c()['bodegaOrigen'].set(1);
    c()['lineas'].set([{ productoId: 7, codigo: 'CAB-12', nombre: 'Cable', cantidad: 10 }]);
    c()['enviar']();
    const creado = resumen(3, 'ENVIADO', 'EN_TRANSITO');
    backend.expectOne(q => q.url === '/api/traslados-grupo' && q.method === 'POST').flush(detalle(creado));
    backend.expectOne(q => q.url === '/api/traslados-grupo' && q.method === 'GET').flush(lista(creado));
    fixture.detectChanges();
    expect(c()['nuevo']()).toBe(false);
    expect(c()['detalle']().resumen.consecutivo).toBe('TG-0003');
  });

  it('sin destino, bodega o productos no se llama al backend', () => {
    crear(['TRASLADO_GRUPO_ENVIAR']);
    c()['abrirNuevo']();
    backend.expectOne('/api/traslados-grupo/destinos').flush([]);
    c()['enviar']();
    expect(c()['errorEnvio']()).toContain('Elige la empresa destino');
    c()['destinoId'].set(2);
    c()['bodegaOrigen'].set(1);
    c()['enviar']();
    expect(c()['errorEnvio']()).toContain('al menos un producto');
    backend.expectNone(q => q.method === 'POST');
  });

  it('recibir: solo la empresa destino en tránsito; cantidad enviada por defecto y manda la bodega', () => {
    crear(['TRASLADO_GRUPO_RECIBIR']);
    const r = resumen(2, 'RECIBIDO', 'EN_TRANSITO');
    abrir(2, r);
    expect(textos()).toContain('Recibir');
    expect(textos()).not.toContain('Anular traslado');
    expect(c()['recibidas']()).toEqual({ 91: 10 });

    c()['recibir']();
    expect(c()['errorAccion']()).toContain('bodega');
    c()['bodegaDestino'].set(2);
    c()['fijarRecibida'](91, 8);
    c()['recibir']();
    const p = backend.expectOne('/api/traslados-grupo/2/recibir');
    expect(p.request.body).toEqual({ bodegaDestinoId: 2, lineas: [{ lineaId: 91, cantidadRecibida: 8 }] });
    p.flush({ resumen: { ...r, estado: 'RECIBIDO' }, lineas: [{ id: 91, codigo: 'CAB-12', nombre: 'Cable', cantidad: 10, cantidadRecibida: 8, faltante: 2 }] });
    backend.expectOne(q => q.url === '/api/traslados-grupo' && q.method === 'GET').flush(lista({ ...r, estado: 'RECIBIDO' }));
    fixture.detectChanges();
    expect(textos()).not.toContain('Recibir');
    expect(html().textContent).toContain('Faltante');
  });

  it('anular: solo la empresa origen en tránsito, con motivo obligatorio', () => {
    crear(['TRASLADO_GRUPO_ENVIAR']);
    const r = resumen(1, 'ENVIADO', 'EN_TRANSITO');
    abrir(1, r);
    expect(textos()).toContain('Anular traslado');
    expect(textos()).not.toContain('Recibir');

    c()['anular']();
    expect(c()['errorAccion']()).toContain('motivo');
    backend.expectNone(q => q.method === 'POST');
    c()['motivo'].set('Se envió por error');
    c()['anular']();
    const p = backend.expectOne('/api/traslados-grupo/1/anular');
    expect(p.request.body).toEqual({ motivo: 'Se envió por error' });
    p.flush({ resumen: { ...r, estado: 'ANULADO' }, motivoAnulacion: 'Se envió por error', lineas: [] });
    backend.expectOne(q => q.url === '/api/traslados-grupo' && q.method === 'GET').flush(lista({ ...r, estado: 'ANULADO' }));
    fixture.detectChanges();
    expect(textos()).not.toContain('Anular traslado');
  });

  it('los botones dependen del permiso: con ambos permisos pero un traslado ya cerrado no hay acciones', () => {
    crear(['TRASLADO_GRUPO_ENVIAR', 'TRASLADO_GRUPO_RECIBIR']);
    abrir(1, resumen(1, 'ENVIADO', 'RECIBIDO'));
    expect(textos()).not.toContain('Recibir');
    expect(textos()).not.toContain('Anular traslado');
  });

  it('sin el permiso de enviar no se ofrece anular, y sin el de recibir no se ofrece recibir', () => {
    crear(['TRASLADO_GRUPO_RECIBIR']);
    abrir(1, resumen(1, 'ENVIADO', 'EN_TRANSITO'));
    expect(textos()).not.toContain('Anular traslado');
    abrir(2, resumen(2, 'RECIBIDO', 'EN_TRANSITO'));
    expect(textos()).toContain('Recibir');

    c()['cerrarDetalle']();
    c()['sesion'].permisos.set(new Set(['TRASLADO_GRUPO_ENVIAR']));
    abrir(2, resumen(2, 'RECIBIDO', 'EN_TRANSITO'));
    expect(textos()).not.toContain('Recibir');
  });
});
