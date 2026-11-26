import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { CotizacionPublica } from '../../core/api/modelos';
import { CotizarEnlace } from './cotizar-enlace';

const datos = (claveSitio = ''): CotizacionPublica => ({
  empresa: 'Ferretería Demo', proveedor: 'Eléctricos del Norte', turnstileClaveSitio: claveSitio,
  rfq: { id: 3, consecutivo: 'RFQ-000003', estado: 'ABIERTA', fechaLimite: '2026-12-01T18:00:00',
    lineas: [{ id: 5, productoNombre: 'Cable 12', productoCodigo: 'CAB-12', cantidad: 10, unidadCodigo: 'm' }] },
});

/** Spec 031: el proveedor cotiza desde su enlace, sin cuenta. */
describe('CotizarEnlace', () => {
  let fixture: ComponentFixture<CotizarEnlace>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (respuesta: CotizacionPublica | null) => {
    fixture = TestBed.createComponent(CotizarEnlace);
    fixture.componentRef.setInput('token', '7.firma');
    fixture.detectChanges();
    const r = backend.expectOne('/api/publico/cotizar/7.firma');
    if (respuesta) {
      r.flush(respuesta);
    } else {
      r.flush({ codigo: 'NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
    }
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('muestra la empresa y los productos, guarda, envía con el campo trampa vacío y agradece', () => {
    crear(datos());
    expect(texto()).toContain('Ferretería Demo');
    expect(texto()).toContain('Cable 12');
    expect((fixture.nativeElement as HTMLElement).querySelector('.trampa input')).not.toBeNull();

    c()['cambiar'](5, 'precio', 2500);
    c()['guardar'](true);
    const guardar = backend.expectOne(r => r.method === 'PUT' && r.url === '/api/publico/cotizar/7.firma');
    expect(guardar.request.body.lineas).toEqual([{ rfqLineaId: 5, cantidadOfertada: 10, unidadOfertada: 'm',
      factorConversion: 1, precioUnitarioOfertado: 2500, impuestoPct: 19 }]);
    guardar.flush({ id: 1, estado: 'BORRADOR', subtotal: 25000, impuestos: 4750, total: 29750, lineas: [] });
    const enviar = backend.expectOne('/api/publico/cotizar/7.firma/enviar');
    expect(enviar.request.body).toEqual({ turnstileToken: undefined, sitioWeb: '' });
    enviar.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(texto()).toContain('¡Cotización enviada!');
  });

  it('un enlace vencido, usado o inventado muestra que ya no está disponible', () => {
    crear(null);
    expect(texto()).toContain('Este enlace ya no está disponible');
    expect(texto()).not.toContain('Enviar cotización');
  });

  it('con anti-bot configurado no deja enviar hasta resolver el reto', () => {
    crear(datos('clave-publica'));
    expect(c()['puedeEnviar']()).toBe(false);
    c()['tokenTurnstile'].set('tok');
    expect(c()['puedeEnviar']()).toBe(true);
  });
});
