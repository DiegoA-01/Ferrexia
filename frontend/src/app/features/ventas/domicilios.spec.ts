import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { Domicilio } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { Domicilios } from './domicilios';

const pendiente: Domicilio = { id: 1, consecutivo: 'DOM-000001', venta: 'C1-000010', cliente: 'Ana', total: 25000,
  direccion: 'Calle 10 # 4-21', telefono: '3001234567', referencia: 'Portería', pago: 'CONTRAENTREGA', estado: 'PENDIENTE',
  creadoEn: new Date().toISOString(), porCobrar: false, eventos: [] };

/** Spec 032: tablero de domicilios. */
describe('Domicilios', () => {
  let fixture: ComponentFixture<Domicilios>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  /**
   * @param permisos permisos de la sesión
   * @param lista    domicilios que responde el backend
   */
  const crear = (permisos: string[], lista: Domicilio[] = [pendiente]) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: { tienePermiso: (p: string) => permisos.includes(p), soloLectura: () => false,
          usuario: signal({ id: 7 }), empresa: signal(null) } }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Domicilios);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/domicilios').flush(lista);
    if (permisos.includes('DOMICILIO_GESTIONAR')) {
      backend.expectOne('/api/domicilios/envio').flush({ productoId: 30, codigo: 'ENVIO', nombre: 'Envío a domicilio', precio: 5000 });
    }
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('quien gestiona ve el envío, asigna un domiciliario externo y cancela con motivo', () => {
    crear(['DOMICILIO_GESTIONAR']);
    expect(texto()).toContain('DOM-000001');
    expect(texto()).toContain('Contraentrega');
    expect(texto()).toContain('Precio actual');
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="tel:3001234567"]')).not.toBeNull();
    c()['abrir']('asignar', pendiente);
    backend.expectOne('/api/domicilios/domiciliarios').flush([{ id: 7, nombre: 'Pedro' }]);
    c()['externoNombre'].set('Mensajería Rápida');
    c()['asignar'](pendiente);
    const r = backend.expectOne(x => x.url === '/api/domicilios/1/asignar');
    expect(r.request.body).toEqual({ nombre: 'Mensajería Rápida', telefono: undefined });
    r.flush({ ...pendiente, estado: 'ASIGNADO', domiciliario: 'Mensajería Rápida' });
    fixture.detectChanges();
    expect(texto()).toContain('Asignado');

    c()['cambiar'](pendiente, 'CANCELADO', ' El cliente ya no lo quiere ');
    const cancelar = backend.expectOne('/api/domicilios/1/estado');
    expect(cancelar.request.body).toEqual({ estado: 'CANCELADO', nota: 'El cliente ya no lo quiere' });
    cancelar.flush({ ...pendiente, estado: 'CANCELADO' });
  });

  it('el domiciliario ve solo lo suyo y lo marca en camino; no asigna ni cancela', () => {
    const suyo = { ...pendiente, estado: 'ASIGNADO' as const, domiciliarioUsuarioId: 7, domiciliario: 'Pedro' };
    crear(['DOMICILIO_REPARTIR'], [suyo]);
    expect(texto()).toContain('Salió (en camino)');
    expect(texto()).not.toContain('Reasignar');
    expect(texto()).not.toContain('Cancelar');
    c()['cambiar'](suyo, 'EN_CAMINO', null);
    backend.expectOne('/api/domicilios/1/estado').flush({ ...suyo, estado: 'EN_CAMINO' });
    fixture.detectChanges();
    expect(texto()).toContain('Entregado');
  });

  it('quien cobra recibe la contraentrega entregada en su turno', () => {
    const entregado = { ...pendiente, estado: 'ENTREGADO' as const, porCobrar: true };
    crear(['VENTA_COBRAR'], [entregado]);
    expect(texto()).toContain('Cobrar');
    c()['abrir']('cobrar', entregado);
    c()['medioCobro'].set('TRANSFERENCIA');
    c()['cobrar'](entregado);
    const r = backend.expectOne('/api/domicilios/1/cobrar');
    expect(r.request.body).toEqual({ pagos: [{ medio: 'TRANSFERENCIA', monto: 25000, referencia: undefined }] });
    r.flush({ ...entregado, porCobrar: false, cobradoEn: new Date().toISOString() });
    fixture.detectChanges();
    expect(texto()).toContain('cobrado');
  });
});
